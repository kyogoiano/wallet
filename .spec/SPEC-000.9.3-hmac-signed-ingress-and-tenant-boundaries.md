# 📐 Specification: SPEC-000.9.3 — HMAC-Signed Ingress & Multi-Tenant Security Boundary

- **Status**: 🟢 **Ratified**
- **Author**: Antigravity Platform Security & Edge Architecture Guild
- **Date**: 2026-09-13
- **Target Release**: Wallet Service V4 — Phase 000.9.3
- **Bounded Context**: `:edge` (`br.com.wallet.edge`), `:core` (`br.com.wallet.core`), and Root Transactional Ledger (`br.com.wallet.ledger` / `br.com.wallet.infrastructure`)
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly focused on HMAC ingress verification, zero-DB key resolution, tenant rate limiting, and Core transactional boundary enforcement.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history50.txt`](file:///.histories/history50.txt): Identified spoofable `X-Tenant-Id`, SSE authorization stubs, noisy-neighbor capacity starvation, and lack of tenant isolation in Core. Established principle: authenticate at Edge, derive tenant cryptographically, enforce ownership at Core.
  - [`.histories/history51.txt`](file:///.histories/history51.txt): Ratified **HMAC-SHA-256** as sole V1 ingress auth; eliminated JWT/JWKS; mandated JDK 27 crypto (`javax.crypto.Mac`); adopted replay protection via short timestamp window ($\pm 30$s) and `operationId`.
  - [`.histories/history52.txt`](file:///.histories/history52.txt): Ratified versioned canonical format (`WALLET-HMAC-V1`); removed redundant `X-Nonce`; standardized epoch millis; decoupled credential metadata from secret material; instituted `I-SEC-009` (trusted Edge origin on NATS); refined HTTP status taxonomy (`401` vs `403`).
  - [`.histories/history58.txt`](file:///.histories/history58.txt): Hardened canonicalization (RFC 3986 path, sorted query, byte hashing), clarified fetch-based SSE over native EventSource, defined bounded RateLimiter cardinality (fail-closed), upgraded credential rotation to MUST, grounded NATS publisher isolation, and instituted `I-SEC-010` (tenant-scoped fraud state).
  - [`SPEC-000.9.1`](file:///.spec/SPEC-000.9.1-edge-core-independent-runtimes.md): Established independent OS runtimes and Zero-DB Edge footprint (`I-STATE-001`).

---

## 1. Intent & Business Value

In Phase 000.9.1, `X-Tenant-Id` was introduced as an unverified header. Raw headers are spoofable, unenforced across routes, and disconnected from Core account ownership.
This specification formalizes **end-to-end multi-tenant security**:
1. Mutating requests (`POST /operations/*`) and SSE status streams (`GET /operations/{opId}/stream`) must present an **HMAC-SHA-256 signature** over a versioned canonical request (`WALLET-HMAC-V1`) using JDK 27 standard cryptography (`javax.crypto.Mac`). SSE clients use streaming `fetch()` with headers (native browser `EventSource` without custom headers is not supported).
2. Edge resolves credentials from an immutable in-memory snapshot and derives `tenantId` in $O(1)$ time with **zero database access** (`I-STATE-001`).
3. `PerimeterRateLimiter` partitions capacity by structured key `RateLimitKey(tenantId, principalId)` with bounded cardinality, guaranteeing fair-share isolation.
4. Core enforces **transactional tenant isolation** during `SELECT FOR UPDATE`: mutations reject if participating accounts do not match the command's derived tenant.
5. Core accepts commands exclusively from authenticated Edge transports with dedicated publisher credentials over NATS JetStream (`I-SEC-009`).

---

## 2. Mathematical & System Invariants

- **`I-SEC-001` (No Implicit Identity)**: `X-Tenant-Id` MUST NOT participate in auth, routing, rate limiting, or envelope construction. Tenant identity is derived exclusively from verified credentials:
  $$\text{TenantId} = f(\text{VerifiedCredential}), \quad \text{Identity}(\text{req}) \cap \text{Header}(\text{X-Tenant-Id}) = \emptyset$$
- **`I-SEC-002` (Cryptographic HMAC Ingress)**: Ingress mutation and SSE requests MUST authenticate via versioned HMAC-SHA-256:
  $$\text{Valid}(\text{req}) \iff \text{MessageDigest.isEqual}\Big(\text{HMAC-SHA256}\big(K_{\text{secret}}, \text{Canonical}_{\text{V1}}(\text{req})\big), \text{Header}(\text{X-Signature})\Big)$$
- **`I-SEC-003` (Zero Relational Dependency at Edge)**: Credential resolution and signature verification MUST execute from local memory snapshots without database calls:
  $$\text{Deps}(\text{EdgeSecurity}) \cap \{\text{JDBC}, \text{PostgreSQL}, \text{Hibernate}, \text{HikariCP}\} = \emptyset$$
- **`I-SEC-004` (Tenant Identity Derivation)**: `tenantId` is populated strictly from `CredentialMetadata` associated with `X-Key-Id`. Client cannot choose its tenant.
- **`I-SEC-005` (Core Transactional Tenant Isolation)**: Core must verify inside the atomic transaction that all involved accounts belong to the command tenant:
  $$\forall \text{Tx}, \quad \text{Account}_{\text{src}}.\text{tenantId} = \text{Command}.\text{tenantId} = \text{Account}_{\text{dst}}.\text{tenantId}$$
- **`I-SEC-006` (Replay Admission & Financial Idempotency)**: Replay protection is dual-layered: Edge limits replay admission via timestamp freshness ($|t_{\text{now}} - t_{\text{req}}| \le 30{,}000\text{ms}$), while Core guarantees financial idempotency:
  $$\text{Admit}(\text{req}) \iff |t_{\text{now}} - t_{\text{req}}| \le 30{,}000\text{ms}, \quad \text{FinancialEffects}(\text{operationId}) \le 1$$
- **`I-SEC-007` (Tenant Fair-Share Rate Limiting)**: Token buckets track capacity per `RateLimitKey(tenantId, principalId)` with bounded key cardinality. Saturated cardinality fails closed. V1 limits are per-Edge instance.
- **`I-SEC-008` (Fail-Closed Security Gate)**: Any validation failure fails closed (`HTTP 401`, `HTTP 403`, `HTTP 429`). Unauthenticated fallback is forbidden.
- **`I-SEC-009` (Trusted Edge Origin)**: Core accepts commands only from dedicated Edge publisher identities on NATS with permissions restricted to `commands.wallet.>`. Arbitrary publishers are rejected.
- **`I-SEC-010` (Tenant-Scoped Risk State)**: All tenant-scoped fraud, velocity counters, risk profiles, and graph hot-cache keys MUST namespace with `tenantId` (e.g. `fraud:velocity:{tenantId}:{userId}`).

---

## 3. MoSCoW Requirements

### 3.1 Pillar A: Edge HMAC Verification & Canonicalization [MUST]
- **`REQ-SEC-001` [MUST]**: Financial endpoints (`POST /operations/*`) and SSE streams (`GET /operations/{opId}/stream`) MUST enforce HMAC-SHA-256 authentication using JDK 27 `javax.crypto.Mac` and `MessageDigest.isEqual`. SSE clients MUST use an HTTP streaming client capable of supplying HMAC headers (e.g. `fetch()`). Native browser `EventSource` without headers is not a V1 requirement.
- **`REQ-SEC-002` [MUST]**: Canonical request string MUST adhere to version `WALLET-HMAC-V1`:
  $$\text{"WALLET-HMAC-V1\n"} + \text{Method} + \text{"\n"} + \text{CanonicalPath} + \text{"\n"} + \text{CanonicalQuery} + \text{"\n"} + \text{KeyId} + \text{"\n"} + \text{TimestampMillis} + \text{"\n"} + \text{OpId} + \text{"\n"} + \text{Hex}(\text{SHA256}(\text{Body}))$$
  CanonicalPath is RFC 3986 normalized; CanonicalQuery parameters are sorted lexicographically by encoded key then value (empty query = empty line); Body is exact UTF-8 bytes (empty body = SHA-256 of empty bytes). Separator is `\n`, no trailing newline, lowercase hex for hashes.
- **`REQ-SEC-003` [MUST]**: Reject requests where $|t_{\text{edgeMillis}} - t_{\text{reqMillis}}| > 30{,}000\text{ms}$ with `HTTP 401 UNAUTHORIZED` (`I-SEC-006`). Timestamp freshness applies strictly to initial request admission (not active SSE stream duration).

### 3.2 Pillar B: Zero-DB Key Resolution & Secret Isolation [MUST]
- **`REQ-SEC-004` [MUST]**: Edge MUST resolve `X-Key-Id` from an immutable local memory snapshot. Public `CredentialMetadata` MUST be decoupled from private `CredentialMaterial` to prevent credential leakage in logs or traces (`I-SEC-003`, `I-SEC-004`). Synchronous credential refresh on the request path is forbidden.
- **`REQ-SEC-010` [MUST]**: Zero-Downtime Credential Rotation: Edge MUST support at least two simultaneously valid credential versions in `CredentialSnapshot` with explicit activation and retirement, with zero database access and zero request-path refresh.

### 3.3 Pillar C: Multi-Tenant Fair-Share Rate Limiting [MUST]
- **`REQ-SEC-005` [MUST]**: `PerimeterRateLimiter` MUST partition token buckets by structured `RateLimitKey(tenantId, principalId)` (`I-SEC-007`) with bounded key cardinality (e.g. max 10,000 active keys). Saturated cardinality fails closed (`HTTP 429`). V1 local limits are per-Edge instance.

### 3.4 Pillar D: Edge-to-Core Propagation & SSE Stream Security [MUST]
- **`REQ-SEC-006` [MUST]**: `CommandEnvelope` MUST carry verified `tenantId`, `principalId`, and `keyId`. `NatsEdgeCommandPublisher` MUST inject these attributes into NATS message headers.
- **`REQ-SEC-007` [MUST]**: `OperationStatusAuthorizationFilter` MUST verify that the authenticated principal's `tenantId` matches the operation's tenant before permitting SSE stream subscription (`HTTP 403` on mismatch).
- **`REQ-SEC-017` [MUST]**: NATS Publisher Boundary: Edge publishers MUST use dedicated NATS account credentials restricted to `commands.wallet.>` and status subjects. Core MUST reject commands from arbitrary publishers (`I-SEC-009`).

### 3.5 Pillar E: Core Transactional Tenant Isolation & Schema Evolution [MUST]
- **`REQ-SEC-008` [MUST]**: Database schema (`accounts`, `ledger`, `wallet_operations`) MUST include `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'` with composite indexes `idx_accounts_tenant_id (tenant_id, id)`, `idx_ledger_tenant_id (tenant_id, wallet_id)`, and `idx_wallet_operations_tenant_id (tenant_id, operation_id)`. `DEFAULT 'default'` is strictly for migration compatibility.
- **`REQ-SEC-009` [MUST]**: Core use cases (`TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`) MUST verify account tenant ownership inside the `SELECT FOR UPDATE` transaction. Mismatches throw `TenantMismatchException` and transition operation to `FAILED` (`I-SEC-005`).
- **`REQ-SEC-018` [MUST]**: Tenant-Scoped Risk State: All fraud velocity counters, risk profiles, and graph hot-cache keys MUST namespace with `tenantId` (e.g. `fraud:velocity:{tenantId}:{userId}`) in DragonflyDB (`I-SEC-010`).

### 3.6 Operational Governance [SHOULD / COULD / WON'T]
- **`REQ-SEC-011` [SHOULD]**: Security telemetry MAY include `tenant.id`, `principal.id`, and `operation.id`, but MUST NOT include `CredentialMaterial`, HMAC secret keys, or `X-Signature` headers.
- **`REQ-SEC-012` [COULD]**: DragonflyDB-backed distributed token bucket fallback for multi-replica Edge deployments.
- **`REQ-SEC-013` [COULD]**: PostgreSQL Row-Level Security (RLS) policies as defense-in-depth behind transaction checks.
- **`REQ-SEC-014` [WON'T]**: External OAuth2 / OpenID Connect / Keycloak server dependencies in V1.
- **`REQ-SEC-015` [WON'T]**: Mandatory client-side mTLS certificates for all API consumers.
- **`REQ-SEC-016` [WON'T]**: Embedding `tenantId` into NATS subject taxonomy (`commands.wallet.<tenantId>.<type>`).

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Module | Affected Flow | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`edge`** | Ingress & SSE Stream | Client clock drift $>30$s | `I-SEC-006`: `HTTP 401` with `TIMESTAMP_OUT_OF_RANGE`. |
| **`edge`** | Rate Limiter | High-cardinality principal flood | `I-SEC-007`: Bounded key cardinality fails closed (`HTTP 429`). |
| **`messaging`** | NATS Command Bus | Unauthorized publisher injects command | `I-SEC-009`: NATS credentials restricted to Edge publisher identity. |
| **`ledger`** | Core Transaction | Transfer between differing tenants | `I-SEC-005`: Core verifies account tenant match in DB transaction; throws `TenantMismatchException`. |
| **`fraud`** | Fraud Gate Evaluation | Cross-tenant velocity collision | `I-SEC-010`: Keys namespaced `fraud:velocity:{tenantId}:{userId}` in DragonflyDB. |

---

## 5. Mandatory Test Triad (`I-TDD-002`) & Failure Gates

| Requirement | 1. Positive Canonical Test | 2. Invalid Input / Boundary Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-SEC-001` (`I-SEC-002`) | `HmacAuthFilterTest.shouldAcceptValidSignature()` | Corrupted signature $\to$ HTTP 401 | Timing attack $\to$ Constant-time `MessageDigest.isEqual` |
| `REQ-SEC-002` (Canonical) | `HmacCanonicalizerTest.shouldSortQueryParams()` | Unsorted query $\to$ Deterministic sort | Empty body $\to$ Empty string SHA-256 hash |
| `REQ-SEC-003` (`I-SEC-006`) | `HmacAuthFilterTest.shouldAcceptWithinWindow()` | Skew $> 30{,}000\text{ms} \to$ HTTP 401 | Negative timestamp $\to$ HTTP 401 |
| `REQ-SEC-004` (`I-SEC-004`) | `CredentialResolverTest.shouldDeriveTenantId()` | Unknown keyId $\to$ HTTP 401 | Untrusted `X-Tenant-Id` header ignored (`I-SEC-001`) |
| `REQ-SEC-005` (`I-SEC-007`) | `PerimeterRateLimiterTest.shouldIsolateTenants()` | Tenant A exhausted $\to$ Tenant B succeeds | Cardinality overflow $\to$ Fail closed HTTP 429 |
| `REQ-SEC-007` (SSE Stream) | `EdgeStreamSecurityIT.shouldAuthorizeMatchingTenant()` | Cross-tenant stream $\to$ HTTP 403 | Missing auth $\to$ HTTP 401 |
| `REQ-SEC-009` (`I-SEC-005`) | `TransferFundsUseCaseTenantIT.shouldExecute()` | Source account wrong tenant $\to$ Reject | Target account wrong tenant $\to$ Rollback TX |
| `REQ-SEC-010` (Rotation) | `CredentialResolverTest.shouldSupportDualKeys()` | Deprecated key during grace $\to$ Accept | Retired key $\to$ HTTP 401 |

---

## 6. Acceptance Criteria

- [x] External financial mutation endpoints reject requests lacking valid HMAC signatures with `HTTP 401`.
- [x] SSE stream endpoint enforces fetch-based HMAC headers and tenant ownership matching the authenticated principal (`REQ-SEC-001`, `REQ-SEC-007`).
- [x] Edge verifies signatures in $O(1)$ time with zero JDBC/PostgreSQL dependencies (`I-SEC-003`).
- [x] Canonical format enforces `WALLET-HMAC-V1` with normalized path, sorted query, and body SHA-256 digest (`REQ-SEC-002`).
- [x] Tenant identity is derived exclusively from credentials; raw `X-Tenant-Id` headers cannot override tenant (`I-SEC-001`, `I-SEC-004`).
- [x] `PerimeterRateLimiter` enforces independent quotas per `RateLimitKey` with bounded cardinality (`I-SEC-007`, `REQ-SEC-005`).
- [x] Credential snapshot supports zero-downtime dual-key rotation (`REQ-SEC-010`).
- [x] `CommandEnvelope` carries verified tenant context across NATS JetStream (`REQ-SEC-006`).
- [x] Core use cases reject cross-tenant transfers with `TenantMismatchException` inside database transactions (`I-SEC-005`).
- [x] Core accepts commands exclusively from authenticated Edge publishers over NATS (`I-SEC-009`, `REQ-SEC-017`).
- [x] Database schema includes `tenant_id` on `accounts`, `ledger`, and `wallet_operations` (`REQ-SEC-008`).
- [x] Fraud velocity counters and risk state namespace by `tenantId` in DragonflyDB (`I-SEC-010`, `REQ-SEC-018`).
- [x] Total specification lines do not exceed 250 lines (`I-SDD-006`).
