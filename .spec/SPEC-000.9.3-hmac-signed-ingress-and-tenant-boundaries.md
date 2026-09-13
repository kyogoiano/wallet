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
  - [`SPEC-000.9.1`](file:///.spec/SPEC-000.9.1-edge-core-independent-runtimes.md): Established independent OS runtimes and Zero-DB Edge footprint (`I-STATE-001`).

---

## 1. Intent & Business Value

In Phase 000.9.1, `X-Tenant-Id` was introduced as an unverified header. Raw headers are spoofable, unenforced across routes, and disconnected from Core account ownership.
This specification formalizes **end-to-end multi-tenant security**:
1. Mutating requests (`POST /operations/*`) and SSE status streams (`GET /operations/{opId}/stream`) must present an **HMAC-SHA-256 signature** over a versioned canonical request (`WALLET-HMAC-V1`) using JDK 27 standard cryptography (`javax.crypto.Mac`).
2. Edge resolves credentials from an immutable in-memory snapshot and derives `tenantId` in $O(1)$ time with **zero database access** (`I-STATE-001`).
3. `PerimeterRateLimiter` partitions capacity by structured key `RateLimitKey(tenantId, principalId)`, guaranteeing fair-share isolation.
4. Core enforces **transactional tenant isolation** during `SELECT FOR UPDATE`: mutations reject if participating accounts do not match the command's derived tenant.
5. Core accepts commands exclusively from authenticated Edge transports over NATS JetStream (`I-SEC-009`).

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
- **`I-SEC-006` (Replay Resistance & Freshness)**: A request is admissible only when timestamp is fresh ($\le 30{,}000\text{ms}$) AND `operationId` has not produced a financial effect:
  $$|t_{\text{now}} - t_{\text{req}}| \le 30{,}000\text{ms} \quad \land \quad \text{Executed}(\text{operationId}) = \text{false}$$
- **`I-SEC-007` (Tenant Fair-Share Rate Limiting)**: Token buckets track capacity per `RateLimitKey(tenantId, principalId)`. Tenant exhaustion MUST NOT deplete peer tenant capacity.
- **`I-SEC-008` (Fail-Closed Security Gate)**: Any validation failure fails closed (`HTTP 401`, `HTTP 403`, `HTTP 429`). Unauthenticated fallback is forbidden.
- **`I-SEC-009` (Trusted Edge Origin)**: Core MUST accept commands only from authenticated Edge publishers. NATS headers are trusted only after transport identity is verified.

---

## 3. MoSCoW Requirements

### 3.1 Pillar A: Edge HMAC Verification & Canonicalization [MUST]
- **`REQ-SEC-001` [MUST]**: Financial endpoints (`POST /operations/*`) and SSE streams (`GET /operations/{opId}/stream`) MUST enforce HMAC-SHA-256 authentication using JDK 27 `javax.crypto.Mac` and `MessageDigest.isEqual`.
- **`REQ-SEC-002` [MUST]**: Canonical request string MUST adhere to version `WALLET-HMAC-V1`:
  $$\text{"WALLET-HMAC-V1\n"} + \text{Method} + \text{"\n"} + \text{Path} + \text{"\n"} + \text{Query} + \text{"\n"} + \text{KeyId} + \text{"\n"} + \text{TimestampMillis} + \text{"\n"} + \text{OpId} + \text{"\n"} + \text{Hex}(\text{SHA256}(\text{Body}))$$
- **`REQ-SEC-003` [MUST]**: Reject requests where $|t_{\text{edgeMillis}} - t_{\text{reqMillis}}| > 30{,}000\text{ms}$ with `HTTP 401 UNAUTHORIZED` (`I-SEC-006`). Timestamp freshness applies strictly to initial request admission (not active SSE stream duration).

### 3.2 Pillar B: Zero-DB Key Resolution & Secret Isolation [MUST]
- **`REQ-SEC-004` [MUST]**: Edge MUST resolve `X-Key-Id` from an immutable local memory snapshot. Public `CredentialMetadata` MUST be decoupled from private `CredentialMaterial` to prevent credential leakage in logs or traces (`I-SEC-003`, `I-SEC-004`). Synchronous credential refresh on the request path is forbidden.

### 3.3 Pillar C: Multi-Tenant Fair-Share Rate Limiting [MUST]
- **`REQ-SEC-005` [MUST]**: `PerimeterRateLimiter` MUST partition token buckets by structured `RateLimitKey(tenantId, principalId)` (`I-SEC-007`). Saturated buckets return `HTTP 429` with `Retry-After: 1` without degrading other tenants.

### 3.4 Pillar D: Edge-to-Core Propagation & SSE Stream Security [MUST]
- **`REQ-SEC-006` [MUST]**: `CommandEnvelope` MUST carry verified `tenantId`, `principalId`, and `keyId`. `NatsEdgeCommandPublisher` MUST inject these attributes into NATS message headers. Core trusts headers only via authenticated transport (`I-SEC-009`).
- **`REQ-SEC-007` [MUST]**: `OperationStatusAuthorizationFilter` MUST verify that the authenticated principal's `tenantId` matches the operation's tenant before permitting SSE stream subscription (`HTTP 403` on mismatch).

### 3.5 Pillar E: Core Transactional Tenant Isolation & Schema Evolution [MUST]
- **`REQ-SEC-008` [MUST]**: Database schema (`accounts`, `ledger`, `wallet_operations`) MUST include `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'` with composite index `idx_accounts_tenant_id (tenant_id, id)`. `DEFAULT 'default'` is strictly for migration compatibility and MUST NOT represent a valid authenticated tenant.
- **`REQ-SEC-009` [MUST]**: Core use cases (`TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`) MUST verify account tenant ownership inside the `SELECT FOR UPDATE` transaction. Mismatches throw `TenantMismatchException` and transition operation to `FAILED` (`I-SEC-005`).

### 3.6 Operational Governance [SHOULD / COULD / WON'T]
- **`REQ-SEC-010` [SHOULD]**: Support overlapping credentials for zero-downtime key rotation.
- **`REQ-SEC-011` [SHOULD]**: Tag OpenTelemetry spans with `tenant.id` and `principal.id` (omitting secret key material).
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
| **`edge`** | Rate Limiter | Single tenant traffic surge | `I-SEC-007`: `RateLimitKey` isolates token buckets per tenant. |
| **`messaging`** | NATS Command Bus | Unauthorized publisher injects command | `I-SEC-009`: NATS credentials + TLS authenticate publisher before headers are trusted. |
| **`ledger`** | Core Transaction | Transfer between differing tenants | `I-SEC-005`: Core verifies account tenant match in DB transaction; throws `TenantMismatchException`. |
| **`fraud`** | Fraud Gate Evaluation | Tenant-specific velocity state | Risk profiles and velocity counters partition keys by `tenantId` in DragonflyDB. |

---

## 5. Mandatory Test Triad (`I-TDD-002`) & Failure Gates

| Requirement | 1. Positive Canonical Test | 2. Invalid Input / Boundary Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-SEC-001` (`I-SEC-002`) | `HmacAuthFilterTest.shouldAcceptValidSignature()` | Corrupted signature $\to$ HTTP 401 | Timing attack $\to$ Constant-time `MessageDigest.isEqual` |
| `REQ-SEC-003` (`I-SEC-006`) | `HmacAuthFilterTest.shouldAcceptWithinWindow()` | Skew $> 30{,}000\text{ms} \to$ HTTP 401 | Negative timestamp $\to$ HTTP 401 |
| `REQ-SEC-004` (`I-SEC-004`) | `CredentialResolverTest.shouldDeriveTenantId()` | Unknown keyId $\to$ HTTP 401 | Untrusted `X-Tenant-Id` header ignored (`I-SEC-001`) |
| `REQ-SEC-005` (`I-SEC-007`) | `PerimeterRateLimiterTest.shouldIsolateTenants()` | Tenant A exhausted $\to$ Tenant B succeeds | Memory exhaustion $\to$ Bounded LRU cache |
| `REQ-SEC-007` (SSE Stream) | `EdgeStreamSecurityIT.shouldAuthorizeMatchingTenant()` | Cross-tenant stream $\to$ HTTP 403 | Missing auth $\to$ HTTP 401 |
| `REQ-SEC-009` (`I-SEC-005`) | `TransferFundsUseCaseTenantIT.shouldExecute()` | Source account wrong tenant $\to$ Reject | Target account wrong tenant $\to$ Rollback TX |

---

## 6. Acceptance Criteria

- [ ] External financial mutation endpoints reject requests lacking valid HMAC signatures with `HTTP 401`.
- [ ] Edge verifies signatures in $O(1)$ time with zero JDBC/PostgreSQL dependencies (`I-SEC-003`).
- [ ] Canonical format enforces `WALLET-HMAC-V1` with epoch milliseconds and body SHA-256 digest (`REQ-SEC-002`).
- [ ] Tenant identity is derived exclusively from credentials; raw `X-Tenant-Id` headers cannot override tenant (`I-SEC-001`, `I-SEC-004`).
- [ ] `PerimeterRateLimiter` enforces independent quotas per `RateLimitKey` (`I-SEC-007`).
- [ ] `CommandEnvelope` carries verified tenant context across NATS JetStream (`REQ-SEC-006`).
- [ ] Core use cases reject cross-tenant transfers with `TenantMismatchException` inside database transactions (`I-SEC-005`).
- [ ] Core accepts commands exclusively from authenticated Edge publishers over NATS (`I-SEC-009`).
- [ ] Database schema includes `tenant_id` on `accounts`, `ledger`, and `wallet_operations` (`REQ-SEC-008`).
- [ ] SSE stream endpoint enforces tenant ownership matching the authenticated principal (`REQ-SEC-007`).
- [ ] Total specification lines do not exceed 250 lines (`I-SDD-006`).
