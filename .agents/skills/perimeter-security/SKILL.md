---
name: perimeter-security
description: Cryptographic HMAC request signing, canonicalization, zero-DB credential resolution, multi-tenant rate limiting, and Edge-to-Core tenant boundaries.
---

# 🛡️ Perimeter Security & Multi-Tenant Boundaries Skill

## 1. Identity & Architectural Mantra

This skill guides the design, implementation, and verification of **cryptographic perimeter authentication**, **zero-database key resolution**, **tenant-scoped rate limiting**, and **core transactional isolation** across the Wallet platform.

> *"Edge authenticates cryptographically, derives tenant identity without DB access, and enforces tenant rate limits. Core enforces account ownership inside the database transaction boundary. Zero untrusted headers."*

---

## 2. Security Invariants

| Invariant ID | Name | Formal Invariant Rule |
| :--- | :--- | :--- |
| **`I-SEC-001`** | **No Implicit Identity** | $\text{Identity}(\text{request}) \cap \text{Header}(\text{X-Tenant-Id}) = \emptyset$. `X-Tenant-Id` header MUST NOT participate in auth, routing, rate limiting, or envelope construction. |
| **`I-SEC-002`** | **Cryptographic HMAC Ingress** | $\forall m \in \text{Mutations}, \quad \text{HMAC}_{\text{SHA256}}(\text{Secret}, \text{CanonicalRequest}(m)) \stackrel{?}{=} \text{Header}(\text{X-Signature})$ using constant-time comparison. |
| **`I-SEC-003`** | **Zero DB at Edge** | $\text{Deps}(\text{EdgeSecurity}) \cap \{\text{JDBC}, \text{PostgreSQL}, \text{Hibernate}\} = \emptyset$. Credentials resolved from local memory snapshot in $O(1)$ time. |
| **`I-SEC-004`** | **Tenant Derivation** | $\text{TenantId} = \text{CredentialMetadata}(\text{X-Key-Id}).\text{tenantId}$. Client cannot specify or override tenant. |
| **`I-SEC-005`** | **Core Transactional Isolation**| $\forall \text{Tx}, \quad \text{Account}_{\text{src}}.\text{tenantId} = \text{Command}.\text{tenantId} = \text{Account}_{\text{dst}}.\text{tenantId}$ inside `SELECT FOR UPDATE`. |
| **`I-SEC-006`** | **Replay Resistance & Freshness**| $|\text{nowMillis}() - \text{Timestamp}| \le 30{,}000\text{ms}$ AND `operationId` has not already produced a financial effect (`I-IDEMPOTENCY-001`). |
| **`I-SEC-007`** | **Tenant Fair-Share Limiting** | $\text{RateLimitKey} = (\text{tenantId}, \text{principalId})$. Tenant-specific exhaustion MUST NOT consume another tenant's bucket capacity. |
| **`I-SEC-008`** | **Fail-Closed Security Gate** | Any signature mismatch, invalid key, or clock skew $\to \text{HTTP } 401 \text{ UNAUTHORIZED}$. No unauthenticated fallback. |
| **`I-SEC-009`** | **Trusted Edge Origin** | Core accepts commands only from authenticated Edge transport. NATS headers are trusted only after publisher identity is verified. |

---

## 3. HMAC-SHA256 Canonicalization & Verification Procedure

### 3.1 Required Headers
Every mutating financial request (`POST /operations/*`) and SSE stream (`GET /operations/{opId}/stream`) requires:
- `X-Key-Id`: Public identifier of client credential.
- `X-Timestamp`: Unix epoch milliseconds (`Instant.now().toEpochMilli()`).
- `Idempotency-Key` (or `X-Operation-Id`): UUID operation identifier.
- `X-Signature`: Hex-encoded HMAC-SHA256 signature.

### 3.2 Canonical Request String Construction (Versioned `WALLET-HMAC-V1`)
```text
WALLET-HMAC-V1\n
<HTTP_METHOD>\n
<CANONICAL_PATH>\n
<CANONICAL_QUERY_STRING>\n
<X-Key-Id>\n
<X-Timestamp>\n
<X-Operation-Id>\n
<HEX(SHA256(PAYLOAD_BYTES))>
```
*Note*: For empty request bodies (e.g. `GET /operations/{opId}/stream`), payload hash is `SHA-256("")` = `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

### 3.3 JDK 27 Verification Implementation
```java
public boolean verifySignature(String receivedHexSig, byte[] secret, String canonicalString) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] expectedSigBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
        byte[] receivedSigBytes = HexFormat.of().parseHex(receivedHexSig);
        return MessageDigest.isEqual(expectedSigBytes, receivedSigBytes); // Constant-time
    } catch (Exception e) {
        return false;
    }
}
```

### 3.4 HTTP Security Response Taxonomy
| Failure Scenario | HTTP Status Code | Error Code |
| :--- | :--- | :--- |
| Missing security headers | `401 Unauthorized` | `MISSING_CREDENTIALS` |
| Unknown or inactive key ID | `401 Unauthorized` | `INVALID_CREDENTIAL` |
| HMAC signature mismatch | `401 Unauthorized` | `INVALID_SIGNATURE` |
| Timestamp skew $> 30{,}000\text{ms}$ | `401 Unauthorized` | `TIMESTAMP_OUT_OF_RANGE` |
| Malformed operation ID | `400 Bad Request` | `INVALID_OPERATION_ID` |
| Resource / stream tenant mismatch | `403 Forbidden` | `FORBIDDEN_TENANT_ACCESS` |
| Core account tenant mismatch | `403 Forbidden` | `TENANT_MISMATCH` |
| Rate limit bucket exhausted | `429 Too Many Requests` | `RATE_LIMITED` |

---

## 4. Zero-DB Credential Resolution & Secret Isolation

### 4.1 Data Models (Separation of Public Metadata and Secret Material)
```java
public record CredentialMetadata(
    String keyId,
    String tenantId,
    String principalId,
    Set<String> permissions,
    boolean active
) {}

public final class CredentialMaterial {
    private final byte[] secret;

    public CredentialMaterial(byte[] secret) {
        this.secret = secret.clone();
    }

    public byte[] secret() {
        return secret.clone();
    }
}

public record AuthenticatedPrincipal(
    String principalId,
    String tenantId,
    String keyId,
    Set<String> permissions
) {}
```

### 4.2 CredentialResolver Protocol
- Edge maintains an immutable in-memory snapshot (`ConcurrentHashMap<String, Pair<CredentialMetadata, CredentialMaterial>>`).
- Credential lookup MUST be local memory ($O(1)$). Credential refresh MUST NOT occur synchronously on the request path.
- DragonflyDB may serve as an asynchronous/ephemeral cache source, but never a blocking dependency on the critical path.

---

## 5. Multi-Tenant Rate Limiting & Isolation

### 5.1 Structured RateLimitKey
```java
public record RateLimitKey(String tenantId, String principalId) {}
```

### 5.2 Fair-Share Token Bucket
`PerimeterRateLimiter` partitions capacity per `RateLimitKey`:
```java
if (!perimeterRateLimiter.tryAcquire(new RateLimitKey(principal.tenantId(), principal.principalId()))) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, "1")
            .body(new ErrorResponse("RATE_LIMITED", "Tenant throughput limit reached"));
}
```
*Rule*: Tenant-specific exhaustion MUST NOT consume another tenant's allocated bucket capacity; global infrastructure saturation MAY still affect all tenants.

---

## 6. Authenticated Edge-to-Core Propagation

### 6.1 CommandEnvelope Contract
```java
public record CommandEnvelope(
    UUID operationId,
    CommandType type,
    String payloadJson,
    long timestamp,
    String clientIp,
    String tenantId,
    String principalId,
    String keyId
) {}
```

### 6.2 NATS JetStream Header Injection & Trust Boundary
`NatsEdgeCommandPublisher` publishes to `commands.wallet.<type>` with headers:
- `Nats-Msg-Id`: `<operationId>`
- `tenant_id`: `<tenantId>`
- `principal_id`: `<principalId>`
- `key_id`: `<keyId>`

`CoreCommandConsumer` trusts these headers because the NATS connection itself is secured via authenticated publisher credentials (`I-SEC-009`).

---

## 7. Core Transactional Verification & Schema Isolation

### 7.1 Database Schema Migration
```sql
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE ledger ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE wallet_operations ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_accounts_tenant_id ON accounts(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_ledger_tenant_id ON ledger(tenant_id, wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_operations_tenant_id ON wallet_operations(tenant_id, operation_id);
```
*Note*: `DEFAULT 'default'` is for backward compatibility during schema migration and MUST NOT represent an authenticated production tenant.

### 7.2 In-Transaction Validation (`I-SEC-005`)
Inside `TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`:
```java
if (!sourceAccount.tenantId().equals(command.tenantId()) ||
    !targetAccount.tenantId().equals(command.tenantId())) {
    throw new TenantMismatchException("Account tenant does not match command tenant " + command.tenantId());
}
```

---

## 8. SSE Status Stream Lifetime Semantics
- Timestamp freshness ($\pm 30{,}000\text{ms}$) applies strictly to **request admission** when establishing the SSE HTTP connection.
- Active SSE connections remain open for streaming updates and MUST NOT be terminated by the initial handshake timestamp.

---

## 9. Implementation Guardrails

- **NEVER** query PostgreSQL or HikariCP from Edge security code (`I-SEC-003`).
- **NEVER** trust `X-Tenant-Id` or equivalent client-supplied tenant headers (`I-SEC-001`).
- **NEVER** log, serialize, or emit HMAC secrets or `CredentialMaterial` in traces or logs.
- **NEVER** compare signatures using ordinary `String.equals()`; always use `MessageDigest.isEqual()`.
- **NEVER** perform synchronous credential reload/refresh during request admission.
- **NEVER** use Edge HMAC authentication as a replacement for Core transactional authorization (`I-SEC-005`).
- **NEVER** trust `tenant_id` NATS headers without authenticated transport/publisher verification (`I-SEC-009`).
- **NEVER** create a second financial idempotency mechanism when `operationId` already provides it (`I-SEC-006`).
- **NEVER** introduce JWT/OIDC/Keycloak dependencies for V1.
- **Prefer** standard JDK 27 cryptographic primitives (`javax.crypto.Mac`) over third-party libraries.
