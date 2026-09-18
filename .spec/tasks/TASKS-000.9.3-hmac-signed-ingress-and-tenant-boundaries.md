# 📝 Task Breakdown: TASKS-000.9.3 — HMAC-Signed Ingress & Multi-Tenant Security Boundary

- **Associated Spec**: [`../SPEC-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md`](file:///.spec/SPEC-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md)
- **Associated Plan**: [`../plans/PLAN-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md`](file:///.spec/plans/PLAN-000.9.3-hmac-signed-ingress-and-tenant-boundaries.md)
- **Status**: In Progress
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-SEC-001`, `REQ-SEC-002` (`I-SEC-002`) | `[MUST]` | `HmacSignatureVerifierTest`, `HmacAuthenticationFilterTest` | `TASK-SEC-2.3`, `TASK-SEC-3.1` |
| `REQ-SEC-003` (`I-SEC-006`) | `[MUST]` | `HmacTimestampFreshnessTest` | `TASK-SEC-3.1` |
| `REQ-SEC-004` (`I-SEC-003`, `I-SEC-004`) | `[MUST]` | `InMemoryCredentialResolverTest` | `TASK-SEC-2.2` |
| `REQ-SEC-005` (`I-SEC-007`) | `[MUST]` | `PerimeterRateLimiterTest` | `TASK-SEC-3.2` |
| `REQ-SEC-006` (`I-SEC-009`) | `[MUST]` | `NatsEdgeCommandPublisherSecurityTest` | `TASK-SEC-3.4` |
| `REQ-SEC-007` | `[MUST]` | `OperationStatusAuthorizationFilterTest` | `TASK-SEC-3.3` |
| `REQ-SEC-008` (`I-SEC-005`) | `[MUST]` | `DaoTenantPersistenceTest` | `TASK-SEC-4.1`, `TASK-SEC-4.2` |
| `REQ-SEC-009` (`I-SEC-005`) | `[MUST]` | `TransferFundsUseCaseTenantTest`, `EdgeToCoreTenantIsolationIT` | `TASK-SEC-5.2`, `TASK-SEC-5.3` |
| `REQ-SEC-015` (`I-PLATFORM-001`) | `[MUST]` | `ProcessBoundaryArchitectureTest` | `TASK-SEC-6.1` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing a task, focus strictly on the active task card below. Do not load unrelated modules into memory.

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Shared Core Domain Primitives (`:core`)
- [ ] `TASK-SEC-1.1` [MUST]: Implement foundational security types in `:core`:
  - Create `br.com.wallet.core.exception.TenantMismatchException` extending `RuntimeException`.
  - Create `br.com.wallet.core.security.SecurityHeaders` defining canonical header constants: `X_KEY_ID = "X-Key-Id"`, `X_TIMESTAMP = "X-Timestamp"`, `X_SIGNATURE = "X-Signature"`, `IDEMPOTENCY_KEY = "Idempotency-Key"`, `PROTOCOL_VERSION = "WALLET-HMAC-V1"`.

### Phase 2: Edge Zero-DB Credential Resolution & Canonical HMAC Engine (`:edge`)
- [ ] `TASK-SEC-2.1` [MUST]: Define perimeter domain security models in `:edge`:
  - `CredentialMetadata`: `record CredentialMetadata(String keyId, String tenantId, String principalId, Set<String> permissions, boolean active)`
  - `CredentialMaterial`: `final class CredentialMaterial` wrapping `byte[] secret` with defensive cloning and zero String leakage.
  - `AuthenticatedPrincipal`: `record AuthenticatedPrincipal(String principalId, String tenantId, String keyId, Set<String> permissions)`
  - `RateLimitKey`: `record RateLimitKey(String tenantId, String principalId)`
- [ ] `TASK-SEC-2.2` [MUST]: Implement `CredentialResolver` SPI and `InMemoryCredentialResolver`:
  - Implement $O(1)$ in-memory credential lookup with zero database dependencies (`I-SEC-003`).
  - Implement `InMemoryCredentialResolverTest` verifying retrieval, key miss handling, and zero database queries.
- [ ] `TASK-SEC-2.3` [MUST]: Implement `HmacCanonicalizer` & `HmacSignatureVerifier`:
  - `HmacCanonicalizer`: formats `WALLET-HMAC-V1\n<METHOD>\n<PATH>\n<QUERY>\n<KEY_ID>\n<TIMESTAMP>\n<OP_ID>\n<HEX(SHA256(BODY))>`.
  - `HmacSignatureVerifier`: computes HMAC-SHA-256 via JDK 27 standard `javax.crypto.Mac` and verifies with constant-time `MessageDigest.isEqual` (`I-SEC-002`).
- [ ] `TASK-SEC-2.4` [MUST]: Implement `HmacSignatureVerifierTest`:
  - Positive test: valid canonical string and secret returns true.
  - Boundary test: modified payload or headers returns false.
  - Constant-time test: invalid signature length fails safely without exception.

### Phase 3: Edge Perimeter Security Filter & Rate Limiting (`:edge`)
- [ ] `TASK-SEC-3.1` [MUST]: Implement `HmacAuthenticationFilter`:
  - Intercepts mutating routes (`/transfers`, `/deposits`, `/withdrawals`) and SSE streams (`/operations/{opId}/stream`).
  - Verifies presence of security headers. Returns `401 Unauthorized` (`MISSING_CREDENTIALS`) if missing.
  - Enforces timestamp freshness $|t_{\text{now}} - t_{\text{req}}| \le 30{,}000\text{ms}$ (`I-SEC-006`). Returns `401 Unauthorized` (`TIMESTAMP_OUT_OF_RANGE`) if skewed.
  - Verifies HMAC signature. Returns `401 Unauthorized` (`INVALID_SIGNATURE`) on mismatch.
  - Binds derived `AuthenticatedPrincipal` to request attribute for downstream handlers.
- [ ] `TASK-SEC-3.2` [MUST]: Update `PerimeterRateLimiter` to support `RateLimitKey`:
  - Partition token buckets by `(tenantId, principalId)`.
  - Implement `PerimeterRateLimiterTest`: assert that exhausting tenant A's capacity returns `429 Too Many Requests` while tenant B's traffic is undisturbed (`I-SEC-007`).
- [ ] `TASK-SEC-3.3` [MUST]: Update `OperationStatusAuthorizationFilter`:
  - Intercepts `/operations/{operationId}/stream`.
  - Queries operation tenant via NATS Request-Reply (or local state) and verifies `principal.tenantId().equals(operation.tenantId())`.
  - Rejects cross-tenant snooping with `403 Forbidden` (`FORBIDDEN_TENANT_ACCESS`) (`REQ-SEC-007`).
- [ ] `TASK-SEC-3.4` [MUST]: Update `CommandEnvelope` and `NatsEdgeCommandPublisher`:
  - Include `tenantId`, `principalId`, `keyId` in `CommandEnvelope`.
  - Inject NATS headers `tenant_id`, `principal_id`, `key_id` on publication.

### Phase 4: Database Schema Evolution & Core Data Access (Root Project)
- [ ] `TASK-SEC-4.1` [MUST]: Update `docker/init/schema.sql`:
  - Add `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'` to `accounts`, `ledger`, and `wallet_operations`.
  - Add composite indexes `idx_accounts_tenant_id (tenant_id, id)`, `idx_ledger_tenant_id (tenant_id, wallet_id)`, `idx_wallet_operations_tenant_id (tenant_id, operation_id)`.
- [ ] `TASK-SEC-4.2` [MUST]: Update Domain Records & DAOs:
  - Add `tenantId` field to `Account`, `LedgerEntry`, and `WalletOperation`.
  - Update `AccountDao`, `LedgerDao`, `WalletOperationsDao` to persist and map `tenant_id`.
- [ ] `TASK-SEC-4.3` [MUST]: Implement `DaoTenantPersistenceTest`:
  - Verify that DAOs persist and retrieve records with their respective `tenant_id`.

### Phase 5: Core Transactional Tenant Verification & Command Handling (Root Project)
- [ ] `TASK-SEC-5.1` [MUST]: Update `CoreCommandConsumer`:
  - Extract verified `tenant_id`, `principal_id`, `key_id` headers from NATS message.
  - Reject commands with missing or unauthenticated tenant headers (`I-SEC-009`).
- [ ] `TASK-SEC-5.2` [MUST]: Enforce In-Transaction Tenant Verification (`I-SEC-005`):
  - In `TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`:
    Check `sourceAccount.tenantId().equals(command.tenantId())` and `targetAccount.tenantId().equals(command.tenantId())` inside `SELECT FOR UPDATE` transaction.
  - Throw `TenantMismatchException` and mark operation `FAILED` on violation.
- [ ] `TASK-SEC-5.3` [MUST]: Implement `TransferFundsUseCaseTenantTest` & `EdgeToCoreTenantIsolationIT`:
  - Positive test: same-tenant accounts transfer funds successfully.
  - Invariant breach test: cross-tenant transfer attempt is rejected inside transaction with `TenantMismatchException` without modifying balances or ledger.

### Phase 6: Operational Verification, Convergence & Summary
- [ ] `TASK-SEC-6.1` [MUST]: Verify Architectural Boundaries & Portability:
  - Assert zero third-party crypto/auth SDK dependencies (`I-PLATFORM-001`).
- [ ] `TASK-SEC-6.2` [MUST]: Author `SUMMARY-000.9.3.md`:
  - Bi-directional equivalence reconciliation (`I-SDD-003`).
  - Practical Verification Guide with curl signing examples, test fixtures, and expected responses (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

### 4.1 Security Invariant Gates
- [ ] Client cannot spoof identity via `X-Tenant-Id` header (`I-SEC-001`)
- [ ] Ingress verified cryptographically using HMAC-SHA-256 and constant-time comparison (`I-SEC-002`)
- [ ] Edge performs zero database queries for authentication (`I-SEC-003`)
- [ ] Tenant identity is derived exclusively from verified credentials (`I-SEC-004`)
- [ ] Core enforces tenant equality inside `SELECT FOR UPDATE` database transaction (`I-SEC-005`)
- [ ] Timestamp skew $> 30{,}000\text{ms}$ is rejected with HTTP 401 (`I-SEC-006`)
- [ ] Tenant rate limiting partitions tokens per `(tenantId, principalId)` (`I-SEC-007`)
- [ ] Security failures fail closed with standard HTTP error taxonomy (`I-SEC-008`)
- [ ] Core accepts commands only from authenticated Edge transport origin (`I-SEC-009`)
