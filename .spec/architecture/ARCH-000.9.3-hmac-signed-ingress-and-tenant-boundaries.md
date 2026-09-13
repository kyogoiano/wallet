# 🏛️ System Architecture: ARCH-000.9.3 — HMAC-Signed Ingress & Multi-Tenant Security Boundary

- **Status**: 🟢 **Ratified**
- **Author**: Antigravity Platform Security & Edge Architecture Guild
- **Date**: 2026-09-13
- **Target Systems / Subprojects**: `:edge` (`br.com.wallet.edge`), `:core` (`br.com.wallet.core`), and Root Transactional Ledger (`br.com.wallet.ledger` / `br.com.wallet.infrastructure`)
- **Governing Specs**:
  - [`../SPEC-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md`](file:///.spec/SPEC-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md) (HMAC Ingress, Zero-DB Credentials, Tenant Rate Limiting, and Core Transactional Boundary)
  - [`../SPEC-000.9.1-edge-core-independent-runtimes.md`](file:///.spec/SPEC-000.9.1-edge-core-independent-runtimes.md) (Independent Runtimes & Zero-DB Edge Footprint)
  - [`../SPEC-000.9-reactive-edge-gateway-and-ingress-resilience.md`](file:///.spec/SPEC-000.9-reactive-edge-gateway-and-ingress-resilience.md) (Reactive Ingress, Journal Fsync & SSE)

---

## 1. Executive Summary & Architectural Mantra

> *"The Edge is responsible for proving who is asking and shielding the platform with fair-share tenant rate limits in $O(1)$ CPU-bound time without database dependencies. The Core is responsible for proving what that identity is permitted to touch inside the single atomic database transaction boundary."*

In prior iterations, the platform accepted an unverified `X-Tenant-Id` header, exposing the system to identity spoofing, noisy-neighbor starvation, and cross-tenant data corruption. **ARCH-000.9.3** closes this vulnerability by instituting:
1. **Cryptographic Ingress Verification**: All mutating commands (`POST /operations/*`) and status streams (`GET /operations/{opId}/stream`) require an **HMAC-SHA-256** signature over a versioned canonical request (`WALLET-HMAC-V1`) using standard JDK 27 cryptography (`javax.crypto.Mac`).
2. **Zero-DB Tenant Derivation & Secret Isolation**: Edge resolves credentials from an immutable in-memory snapshot, decoupling public `CredentialMetadata` from private `CredentialMaterial` to prevent credential leakage.
3. **Partitioned Fair-Share Rate Limiting**: `PerimeterRateLimiter` tracks tokens via structured `RateLimitKey(tenantId, principalId)`, preventing noisy neighbors from starving other tenants.
4. **Core Transactional Tenant Isolation**: Core enforces account tenant equality during `SELECT FOR UPDATE` within the PostgreSQL ACID transaction boundary (`I-ATOMICITY-001`).
5. **Trusted Edge Origin**: Core trusts message headers only from authenticated Edge/NATS publisher identities (`I-SEC-009`).

---

## 2. Macro Topology & Cryptographic Trust Boundaries

```mermaid
flowchart TD
    subgraph ClientPerimeter["Client / External Tier (Untrusted Network)"]
        Client["API Client / Partner Service<br/>(Signs with HMAC-SHA256: WALLET-HMAC-V1)"]
    end

    subgraph EdgeBoundary["Edge Gateway Tier (Port 8080 / 8443) - Stateless, Zero-DB"]
        direction TB
        Filter["HmacAuthenticationFilter<br/>1. Check |now - X-Timestamp| <= 30,000ms<br/>2. Resolve Credential from in-memory snapshot<br/>3. Compute Canonical HMAC-SHA256 (WALLET-HMAC-V1)<br/>4. Constant-time comparison (MessageDigest.isEqual)<br/>5. Derive AuthenticatedPrincipal"]
        Limiter["PerimeterRateLimiter<br/>Token Bucket on RateLimitKey(tenantId, principalId)<br/>O(1) Memory, Zero DB"]
        StreamAuth["OperationStatusAuthorizationFilter<br/>Verify principal.tenantId owns operation on connect"]
        Controller["EdgeOperationsController<br/>Construct Authenticated CommandEnvelope"]
        Publisher["NatsEdgeCommandPublisher<br/>Injects Nats-Msg-Id, tenant_id, principal_id, key_id"]

        Filter --> Limiter --> Controller --> Publisher
        Filter --> StreamAuth
    end

    subgraph IPCFabric["Messaging Fabric (NATS JetStream Cluster with TLS & Auth)"]
        NATS["Stream: commands<br/>Subject: commands.wallet.<type><br/>Authenticated Publisher / Consumer Identities"]
    end

    subgraph CoreBoundary["Core Transactional Tier (Port 8081 Mgmt) - Headless"]
        direction TB
        Consumer["CoreCommandConsumer<br/>Verify Publisher Identity (I-SEC-009)<br/>Extract verified tenantId & principalId"]
        FraudGate["FraudGate Pre-Execution Check<br/>Tenant-scoped velocity counters"]
        UseCase["TransferFundsUseCase / DepositFundsUseCase<br/>SELECT ... FOR UPDATE on accounts"]
        TenantCheck{"Tenant Match Check<br/>source.tenantId == cmd.tenantId<br/>target.tenantId == cmd.tenantId?"}
        LedgerAppend["LedgerDao & AccountDao<br/>Append Hash-Chained Entry with tenant_id<br/>Update Projected Balance"]
        Reject["Throw TenantMismatchException (HTTP 403)<br/>Mark Operation FAILED (ACK message)"]

        Consumer --> FraudGate --> UseCase --> TenantCheck
        TenantCheck -->|YES| LedgerAppend
        TenantCheck -->|NO| Reject
    end

    subgraph PersistenceTier["Core Persistence Stores"]
        PG[("PostgreSQL 17/19<br/>accounts (tenant_id)<br/>ledger (tenant_id)<br/>wallet_operations (tenant_id)")]
        DF[("DragonflyDB Cluster<br/>user:{tenantId}:{userId}:risk")]
    end

    Client -->|HTTP/3 or HTTP/2 + HMAC Headers| Filter
    Publisher -->|PubAck (commands.wallet.*)| NATS
    NATS -->|Competing Consumer| Consumer
    FraudGate -.->|Hot Cache| DF
    LedgerAppend -->|Atomic Single Transaction| PG
```

---

## 3. Cryptographic Request Ingress & Canonicalization Protocol

### 3.1 Required Headers
Clients submitting requests to protected endpoints must provide:
- `X-Key-Id`: Public identifier for the API key / credential.
- `X-Timestamp`: Unix epoch milliseconds (e.g. `1789320000000`).
- `Idempotency-Key` (or `X-Operation-Id`): Operation UUID.
- `X-Signature`: Hexadecimal representation of the computed HMAC-SHA-256 signature.

*(Note: `X-Nonce` is omitted as redundant; `operationId` + timestamp window guarantees replay protection without additional protocol overhead).*

### 3.2 Canonical Request Construction (`WALLET-HMAC-V1`)
To prevent signature manipulation, whitespace discrepancies, and version ambiguities, canonicalization prefixes the protocol version:

$$\text{CanonicalString} = \begin{aligned}
& \text{"WALLET-HMAC-V1\n"} \\
+ & \text{Method} + \text{"\n"} \\
+ & \text{CanonicalPath} + \text{"\n"} \\
+ & \text{CanonicalQueryString} + \text{"\n"} \\
+ & \text{X-Key-Id} + \text{"\n"} \\
+ & \text{X-Timestamp} + \text{"\n"} \\
+ & \text{X-Operation-Id} + \text{"\n"} \\
+ & \text{Hex}(\text{SHA-256}(\text{RequestBodyBytes}))
\end{aligned}$$

For requests without a body (e.g. `GET /operations/{opId}/stream`), the body digest is:
$$\text{Hex}(\text{SHA-256}("")) = \text{"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}$$

### 3.3 JDK 27 Cryptographic Baseline (`I-SEC-002`)
Cryptographic primitives strictly use standard JDK 27 APIs (`javax.crypto.Mac`, `java.security.MessageDigest`, `java.util.HexFormat`):
```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(material.secret(), "HmacSHA256"));
byte[] computed = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
byte[] received = HexFormat.of().parseHex(receivedSignatureHex);

if (!MessageDigest.isEqual(computed, received)) { // Constant-time equality
    throw new InvalidSignatureException("Signature mismatch");
}
```

### 3.4 Replay Defense & Freshness Window (`I-SEC-006`)
Freshness is enforced at Edge:
$$\Delta t = |\text{System.currentTimeMillis}() - \text{X-Timestamp}|$$
If $\Delta t > 30{,}000\text{ms}$ (30 seconds), the request is rejected immediately with `HTTP 401 UNAUTHORIZED`.
Replay resistance is completed downstream by `operationId` financial idempotency (`I-IDEMPOTENCY-001`): replaying a valid request within 30 seconds cannot produce a duplicate balance mutation or duplicate ledger record.

### 3.5 HTTP Security Response Taxonomy
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

## 4. Zero-DB Key Resolution & Secret Isolation

### 4.1 Data Models (Separation of Public Metadata and Secret Material)
To guarantee that HMAC secrets are never leaked into logs, serializers, or OpenTelemetry traces:
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

### 4.2 In-Memory Snapshot & Resolution Protocol (`I-SEC-003`, `I-SEC-004`)
- Edge maintains an immutable in-memory snapshot (`ConcurrentHashMap<String, Pair<CredentialMetadata, CredentialMaterial>>`).
- **Zero-DB Constraint**: Credential lookup is local memory ($O(1)$). Credential refresh MUST NOT occur synchronously on the request path.
- `tenantId` is populated directly from the resolved `CredentialMetadata`. Client-supplied `X-Tenant-Id` headers MUST NOT participate in auth, routing, rate limiting, or envelope construction (`I-SEC-001`).

---

## 5. Multi-Tenant Fair-Share Rate Limiting (`I-SEC-007`)

### 5.1 Structured Key
Instead of delimiter-concatenated strings, rate limiting uses a dedicated record:
```java
public record RateLimitKey(String tenantId, String principalId) {}
```

### 5.2 Fair-Share Token Bucket
`PerimeterRateLimiter` partitions capacity per `RateLimitKey`:
```java
if (!perimeterRateLimiter.tryAcquire(new RateLimitKey(principal.tenantId(), principal.principalId()))) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, "1")
            .body(new ErrorResponse("RATE_LIMITED", "Tenant throughput limit exceeded"));
}
```
*Isolation Invariant*: Tenant-specific exhaustion MUST NOT consume another tenant's allocated bucket capacity; global infrastructure saturation MAY still affect all tenants.

---

## 6. Authenticated Edge-to-Core Propagation & NATS Trust Boundary

### 6.1 CommandEnvelope Contract
The `CommandEnvelope` carries cryptographically verified identity attributes:
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
) {
    public CommandEnvelope {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(payloadJson, "payloadJson must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(principalId, "principalId must not be null");
    }
}
```

### 6.2 Trusted Edge Origin (`I-SEC-009`)
- `NatsEdgeCommandPublisher` injects verified headers:
  `Nats-Msg-Id`, `tenant_id`, `principal_id`, `key_id`, `type`.
- The subject remains `commands.wallet.<type>`. Tenant ID is carried within the envelope and headers, avoiding topic cardinality explosion (`REQ-SEC-016`).
- Core trusts these message headers **only because** the NATS transport connection itself is secured via authenticated publisher credentials (`I-SEC-009`).

---

## 7. Core Transactional Tenant Isolation & Schema Evolution

### 7.1 Database Schema Evolution (`REQ-SEC-008`)
```sql
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_accounts_tenant_id ON accounts(tenant_id, id);

ALTER TABLE ledger ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_ledger_tenant_id ON ledger(tenant_id, wallet_id);

ALTER TABLE wallet_operations ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
CREATE INDEX IF NOT EXISTS idx_wallet_operations_tenant_id ON wallet_operations(tenant_id, operation_id);
```
*Migration Note*: `DEFAULT 'default'` is strictly for migration compatibility and MUST NOT represent a valid authenticated tenant in production.

### 7.2 Transactional Verification Sequence (`I-SEC-005`)
Inside `TransferFundsUseCase` within the `SELECT FOR UPDATE` transaction boundary:
```java
Account sourceAccount = accountDao.findByIdForUpdate(transfer.sourceAccountId())
        .orElseThrow(() -> new AccountNotFoundException("Source account not found"));

Account targetAccount = accountDao.findByIdForUpdate(transfer.targetAccountId())
        .orElseThrow(() -> new AccountNotFoundException("Target account not found"));

// Core Transactional Invariant Check (I-SEC-005):
if (!sourceAccount.tenantId().equals(command.tenantId())) {
    throw new TenantMismatchException(
        "Source account tenant [" + sourceAccount.tenantId() + "] does not match command tenant [" + command.tenantId() + "]"
    );
}

if (!targetAccount.tenantId().equals(command.tenantId())) {
    throw new TenantMismatchException(
        "Target account tenant [" + targetAccount.tenantId() + "] does not match command tenant [" + command.tenantId() + "]"
    );
}
```
If an invariant fails:
1. Transaction rolls back cleanly.
2. Operation is marked `FAILED` in `wallet_operations` with code `TENANT_MISMATCH`.
3. Message is ACKed from NATS (permanent business rejection, no infinite retry loop).

---

## 8. SSE Status Stream Authorization (`REQ-SEC-007`)

- Subscribers to `GET /operations/{opId}/stream` provide valid HMAC credentials for request admission.
- `OperationStatusAuthorizationFilter` extracts `AuthenticatedPrincipal`, loads target operation metadata, and asserts `principal.tenantId().equals(operation.tenantId())`.
- **Stream Lifetime Rule**: Timestamp freshness ($\pm 30{,}000\text{ms}$) applies strictly to **initial connection handshake admission**, NOT to active streaming lifetime.

---

## 9. Threat Matrix & Mitigations

| Threat | Attack Vector | Architectural Control |
| :--- | :--- | :--- |
| **Tenant Spoofing** | Attacker injects `X-Tenant-Id: victim` header | `I-SEC-001`, `I-SEC-004`: Header completely ignored; tenant derived strictly from `X-Key-Id`. |
| **Payload Tampering** | MITM alters transfer amount or recipient | `I-SEC-002`: Body hashed with SHA-256 in `WALLET-HMAC-V1`; any byte change invalidates signature. |
| **Replay Attack** | Intercepted valid request replayed within 30s | `I-SEC-006`: Skew $> 30$s rejected by Edge. Replays within 30s handled idempotently via `operationId`. |
| **Timing Attack** | Attacker measures byte-by-byte HMAC comparison | `I-SEC-002`: Standard `MessageDigest.isEqual` provides constant-time comparison. |
| **Secret Leakage** | Logging or tracing exposes HMAC key | Decoupled `CredentialMetadata` from `CredentialMaterial`; secrets never exposed to records. |
| **Noisy Neighbor** | Single tenant floods gateway with requests | `I-SEC-007`: Token buckets partitioned by `RateLimitKey(tenantId, principalId)`. |
| **Cross-Tenant Breach**| Attacker signs under Tenant A targeting Tenant B wallet | `I-SEC-005`: Core verifies account ownership inside `SELECT FOR UPDATE` transaction. |
| **Broker Injection** | Rogue process publishes forged commands to NATS | `I-SEC-009`: NATS transport secured with discrete authenticated publisher credentials. |

---

## 10. Comprehensive Invariants Registry

| Invariant ID | Name | Formal Statement |
| :--- | :--- | :--- |
| **`I-SEC-001`** | No Implicit Identity | $\text{Identity}(\text{req}) \cap \text{Headers} = \emptyset, \quad \text{TenantId} = f(\text{VerifiedCredential})$ |
| **`I-SEC-002`** | Cryptographic HMAC Ingress | $\text{Valid}(\text{req}) \iff \text{MessageDigest.isEqual}\Big(\text{HMAC-SHA256}(K, \text{Canonical}_{\text{V1}}(\text{req})), \text{Sig}\Big)$ |
| **`I-SEC-003`** | Zero DB at Edge | $\text{Deps}(\text{EdgeSecurity}) \cap \{\text{JDBC}, \text{PostgreSQL}, \text{Hibernate}\} = \emptyset$ |
| **`I-SEC-004`** | Tenant Derivation | $\text{CommandEnvelope}.\text{tenantId} = \text{CredentialMap}[\text{KeyId}].\text{tenantId}$ |
| **`I-SEC-005`** | Core Transactional Isolation | $\forall \text{Tx}, \quad \text{Account}_{\text{src}}.\text{tenantId} = \text{Command}.\text{tenantId} = \text{Account}_{\text{dst}}.\text{tenantId}$ |
| **`I-SEC-006`** | Replay Resistance & Freshness | $|t_{\text{current}} - t_{\text{header}}| \le 30{,}000\text{ms} \quad \land \quad \text{Executed}(\text{operationId}) = \text{false}$ |
| **`I-SEC-007`** | Tenant Fair-Share Limiting | $\text{Key} = (\text{tenantId}, \text{principalId}), \quad \text{Tokens}(\text{Tenant A}) \perp \text{Tokens}(\text{Tenant B})$ |
| **`I-SEC-008`** | Fail-Closed Security Gate | $\text{Invalid}(\text{Signature} \lor \text{Key} \lor \text{Time} \lor \text{Tenant}) \implies \text{Reject Closed} (\text{HTTP } 401/403)$ |
| **`I-SEC-009`** | Trusted Edge Origin | $\text{CoreTrust}(\text{CommandHeaders}) \iff \text{Authenticated}(\text{EdgePublisherTransport})$ |
