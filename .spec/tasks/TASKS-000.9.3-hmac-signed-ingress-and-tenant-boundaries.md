# 📝 Task Breakdown: TASKS-000.9.3 — HMAC-Signed Ingress & Multi-Tenant Security Boundary

- **Associated Spec**: [`../SPEC-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md`](file:///.spec/SPEC-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md)
- **Associated Plan**: [`../plans/PLAN-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md`](file:///.spec/plans/PLAN-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md)
- **Status**: ✅ Completed
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-SEC-001` (`I-SEC-001`) | `[MUST]` | `HmacAuthenticationFilterTest.shouldIgnoreUntrustedTenantHeader()` | `TASK-SEC-2.6`, `TASK-SEC-3.1` |
| `REQ-SEC-001`, `REQ-SEC-002` (`I-SEC-002`) | `[MUST]` | `HmacSignatureVerifierTest`, `HmacAuthenticationFilterTest`, `HmacCanonicalizerTest` | `TASK-SEC-2.3`, `TASK-SEC-2.4`, `TASK-SEC-3.1` |
| `REQ-SEC-003` (`I-SEC-006`) | `[MUST]` | `HmacTimestampFreshnessTest` | `TASK-SEC-3.1` |
| `REQ-SEC-004`, `REQ-SEC-010` (`I-SEC-003`, `I-SEC-004`)| `[MUST]` | `InMemoryCredentialResolverTest`, `CredentialRotationTest` | `TASK-SEC-2.2`, `TASK-SEC-2.5` |
| `REQ-SEC-005` (`I-SEC-007`) | `[MUST]` | `PerimeterRateLimiterTest` | `TASK-SEC-3.2` |
| `REQ-SEC-006`, `REQ-SEC-017` (`I-SEC-009`) | `[MUST]` | `NatsEdgeCommandPublisherSecurityTest`, `NatsPublisherAuthorizationIT` | `TASK-SEC-3.4`, `TASK-SEC-5.1`, `TASK-SEC-5.5` |
| `REQ-SEC-007` | `[MUST]` | `OperationStatusAuthorizationFilterTest` | `TASK-SEC-3.3` |
| `REQ-SEC-008` (`I-SEC-005`) | `[MUST]` | `DaoTenantPersistenceTest` | `TASK-SEC-4.1`, `TASK-SEC-4.2` |
| `REQ-SEC-009` (`I-SEC-005`) | `[MUST]` | `TransferFundsUseCaseTenantTest`, `EdgeToCoreTenantIsolationIT` | `TASK-SEC-5.2`, `TASK-SEC-5.3` |
| `REQ-SEC-018` (`I-SEC-010`) | `[MUST]` | `TenantScopedFraudStateTest` | `TASK-SEC-5.4` |
| `REQ-SEC-014`, `REQ-SEC-015`, `I-PLATFORM-001` | `[MUST]` | `ProcessBoundaryArchitectureTest` | `TASK-SEC-6.1` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing a task, focus strictly on the active task card below. Do not load unrelated modules into memory.

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Shared Core Domain Primitives (`:core`)
- [x] `TASK-SEC-1.1` [MUST]: Implement foundational security types in `:core`:
  - Create `br.com.wallet.core.exceptions.TenantMismatchException` extending `RuntimeException`.
  - Create `br.com.wallet.core.security.SecurityHeaders` defining canonical header constants: `X_KEY_ID = "X-Key-Id"`, `X_TIMESTAMP = "X-Timestamp"`, `X_SIGNATURE = "X-Signature"`, `IDEMPOTENCY_KEY = "Idempotency-Key"`, `PROTOCOL_VERSION = "WALLET-HMAC-V1"`.
  - Unify idempotency identity: HTTP header is `Idempotency-Key`, domain/NATS field is `operationId`, and canonical HMAC field is `OPERATION-ID` (value of `Idempotency-Key`). Exactly one financial idempotency identity exists.

### Phase 2: Edge Zero-DB Credential Resolution & Canonical HMAC Engine (`:edge`)
- [x] `TASK-SEC-2.1` [MUST]: Define perimeter domain security models in `:edge`:
  - `CredentialMetadata`: `record CredentialMetadata(String keyId, String tenantId, String principalId, Set<String> permissions, boolean active)`
  - `CredentialMaterial`: `final class CredentialMaterial` wrapping `byte[] secret` with defensive cloning and zero String leakage.
  - `AuthenticatedPrincipal`: `record AuthenticatedPrincipal(String principalId, String tenantId, String keyId, Set<String> permissions)`
  - `RateLimitKey`: `record RateLimitKey(String tenantId, String principalId)`
  - `CredentialSnapshot`: Immutable record holding active and retiring credential pairs for rotation (`REQ-SEC-010`).
- [x] `TASK-SEC-2.2` [MUST]: Implement `CredentialResolver` SPI and `InMemoryCredentialResolver`:
  - Define `record ResolvedCredential(CredentialMetadata metadata, CredentialMaterial material)`
  - Implement $O(1)$ in-memory credential lookup with zero database dependencies (`I-SEC-003`).
  - Support overlapping active key versions during rotation (`REQ-SEC-010`).
  - Implement `InMemoryCredentialResolverTest` verifying retrieval, key miss handling, and zero database queries.
- [x] `TASK-SEC-2.3` [MUST]: Implement `HmacCanonicalizer` & `HmacSignatureVerifier`:
  - `HmacCanonicalizer`: RFC 3986 path normalization, lexicographically sorted query params (empty query = empty line), exact UTF-8 body bytes (empty body = SHA-256 of empty string), lowercase hex formatting, no trailing newline (`REQ-SEC-002`).
  - `HmacSignatureVerifier`: computes HMAC-SHA-256 via JDK 27 standard `javax.crypto.Mac` and verifies with constant-time `MessageDigest.isEqual` (`I-SEC-002`).
- [x] `TASK-SEC-2.4` [MUST]: Implement `HmacSignatureVerifierTest` & `HmacCanonicalizerTest`:
  - Positive test: valid canonical string and secret returns true.
  - Query sorting test: `?b=2&a=1` and `?a=1&b=2` produce identical canonical queries.
  - Boundary test: modified payload or headers returns false.
  - Constant-time test: invalid signature length fails safely without exception.
- [x] `TASK-SEC-2.5` [MUST]: Implement `CredentialRotationTest`:
  - Assert that incoming requests signed with old key (during transition) and new key are both accepted; retired keys return 401 (`REQ-SEC-010`).
- [x] `TASK-SEC-2.6` [MUST]: Implement `HmacAuthenticationFilterTest.shouldIgnoreUntrustedTenantHeader()`:
  - Assert that client-provided `X-Tenant-Id: malicious-tenant` is completely ignored; tenant is derived strictly from resolved `CredentialMetadata` (`I-SEC-001`).

### Phase 3: Edge Perimeter Security Filter & Rate Limiting (`:edge`)
- [x] `TASK-SEC-3.1` [MUST]: Implement `HmacAuthenticationFilter`:
  - Intercepts mutating routes (`/transfers`, `/deposits`, `/withdrawals`) and SSE streams (`/operations/{opId}/stream`).
  - Supports streaming `fetch()` clients for SSE with custom headers (`REQ-SEC-001`).
  - Verifies presence of security headers. Returns `401 Unauthorized` (`MISSING_CREDENTIALS`) if missing.
  - Enforces timestamp freshness $|t_{\text{now}} - t_{\text{req}}| \le 30{,}000\text{ms}$ (`I-SEC-006`). Returns `401 Unauthorized` (`TIMESTAMP_OUT_OF_RANGE`) if skewed. Freshness applies to initial connection admission.
  - Verifies HMAC signature. Returns `401 Unauthorized` (`INVALID_SIGNATURE`) on mismatch.
  - Binds derived `AuthenticatedPrincipal` to request attribute for downstream handlers.
- [x] `TASK-SEC-3.2` [MUST]: Update `PerimeterRateLimiter` to support `RateLimitKey` and Bounded Cardinality:
  - Partition token buckets by `RateLimitKey(tenantId, principalId)`.
  - Enforce bounded key cardinality (max 10,000 active keys) to prevent memory exhaustion; fail closed with `HTTP 429` (`RATE_LIMIT_SATURATED`) on overflow.
  - Implement `PerimeterRateLimiterTest`: assert that exhausting tenant A + principal A returns `429 Too Many Requests` while tenant B + principal B and tenant A + principal C remain undisturbed (`I-SEC-007`).
- [x] `TASK-SEC-3.3` [MUST]: Update `OperationStatusAuthorizationFilter`:
  - Intercepts `/operations/{operationId}/stream`.
  - Resolves operation tenant ownership exclusively through authenticated Edge→Core NATS request/reply interaction (`REQ-SEC-007`).
  - Edge MUST NOT query PostgreSQL directly; Core response is authoritative for operation ownership.
  - Rejects cross-tenant snooping with `403 Forbidden` (`FORBIDDEN_TENANT_ACCESS`).
- [x] `TASK-SEC-3.4` [MUST]: Update `CommandEnvelope` and `NatsEdgeCommandPublisher`:
  - Include `tenantId`, `principalId`, `keyId` in `CommandEnvelope`.
  - Inject NATS headers `tenant_id`, `principal_id`, `key_id` on publication using dedicated publisher credentials (`REQ-SEC-017`, `I-SEC-009`).

### Phase 4: Database Schema Evolution & Core Data Access (Root Project)
- [x] `TASK-SEC-4.1` [MUST]: Update `docker/init/schema.sql`:
  - Add `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'` to `accounts`, `ledger`, and `wallet_operations`.
  - Add composite indexes `idx_accounts_tenant_id (tenant_id, id)`, `idx_ledger_tenant_id (tenant_id, wallet_id)`, and unique index `idx_wallet_operations_tenant_id (tenant_id, operation_id)`.
- [x] `TASK-SEC-4.2` [MUST]: Update Domain Records & DAOs:
  - Add `tenantId` field to `Account`, `LedgerEntry`, and `WalletOperation`.
  - Update `AccountDao`, `LedgerDao`, `WalletOperationsDao` to persist and map `tenant_id`.
- [x] `TASK-SEC-4.3` [MUST]: Implement `DaoTenantPersistenceTest`:
  - Verify that DAOs persist and retrieve records with their respective `tenant_id`.

### Phase 5: Core Transactional Tenant Verification & Command Handling (Root Project)
- [x] `TASK-SEC-5.1` [MUST]: Update `CoreCommandConsumer`:
  - Extract verified `tenant_id`, `principal_id`, `key_id` headers from NATS message.
  - Reject commands with missing or unauthenticated tenant headers, or from unauthorized publisher identities (`REQ-SEC-017`, `I-SEC-009`).
- [x] `TASK-SEC-5.2` [MUST]: Enforce In-Transaction Tenant Verification (`I-SEC-005`):
  - In `TransferFundsUseCase`: assert `sourceAccount.tenantId().equals(command.tenantId()) && targetAccount.tenantId().equals(command.tenantId())`.
  - In `DepositFundsUseCase`: assert `targetAccount.tenantId().equals(command.tenantId())`.
  - In `WithdrawFundsUseCase`: assert `sourceAccount.tenantId().equals(command.tenantId())`.
  - Throw `TenantMismatchException` and mark operation `FAILED` on violation.
- [x] `TASK-SEC-5.3` [MUST]: Implement `TransferFundsUseCaseTenantTest` & `EdgeToCoreTenantIsolationIT`:
  - Positive test: same-tenant accounts transfer funds successfully.
  - Invariant breach test: cross-tenant transfer attempt is rejected inside transaction with `TenantMismatchException` without modifying balances or ledger.
- [x] `TASK-SEC-5.4` [MUST]: Implement Tenant-Scoped Risk State in Fraud Subsystem (`I-SEC-010`, `REQ-SEC-018`):
  - Namespace DragonflyDB velocity counters and risk keys under `fraud:{tenantId}:...` and `risk:{tenantId}:...`.
  - Implement `TenantScopedFraudStateTest`: assert that user ID collisions across differing tenants do not share velocity counters or block decisions.
- [x] `TASK-SEC-5.5` [MUST]: Implement `NatsPublisherAuthorizationIT` (`I-SEC-009`, `REQ-SEC-017`):
  - Assert that commands published with authorized Edge NATS credentials are accepted and processed.
  - Assert that commands published with forged tenant header are rejected or derived from authenticated transport context.
  - Assert that commands from unknown / unauthorized NATS identities or without authenticated transport are rejected.
  - Assert that non-Edge services attempting to publish to `commands.wallet.>` are rejected.

### Phase 6: Operational Verification, Convergence & Summary
- [x] `TASK-SEC-6.1` [MUST]: Verify Architectural Boundaries & Portability (`I-PLATFORM-001`, `REQ-SEC-014`, `REQ-SEC-015`):
  - Assert Edge/Core process boundary remains intact.
  - Assert Edge has zero relational database dependencies (`I-STATE-001`, `I-SEC-003`).
  - Assert zero third-party crypto/auth SDK dependencies introduced (pure JDK 27 standard libraries).
  - Assert zero JWT/OIDC/Keycloak dependency introduced (`REQ-SEC-014`).
  - Assert mandatory client mTLS is not introduced (`REQ-SEC-015`).
- [x] `TASK-SEC-6.2` [MUST]: Author `SUMMARY-000.9.3.md`:
  - Bi-directional equivalence reconciliation (`I-SDD-003`).
  - Practical Verification Guide with curl signing examples, test fixtures, and expected responses (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

### 4.1 Security Invariant Gates
- [x] Client cannot spoof identity via `X-Tenant-Id` header (`I-SEC-001`)
- [x] Ingress verified cryptographically using HMAC-SHA-256 and constant-time comparison (`I-SEC-002`)
- [x] Edge performs zero database queries for authentication (`I-SEC-003`)
- [x] Tenant identity is derived exclusively from verified credentials (`I-SEC-004`)
- [x] Core enforces tenant equality inside `SELECT FOR UPDATE` database transaction (`I-SEC-005`)
- [x] Timestamp skew $> 30{,}000\text{ms}$ is rejected with HTTP 401 (`I-SEC-006`)
- [x] Tenant rate limiting partitions tokens per `(tenantId, principalId)` with bounded cardinality (`I-SEC-007`)
- [x] Security failures fail closed with standard HTTP error taxonomy (`I-SEC-008`)
- [x] Core accepts commands only from authenticated Edge transport origin (`I-SEC-009`)
- [x] All fraud velocity counters and risk keys are namespaced with `tenantId` in DragonflyDB (`I-SEC-010`)
