# 📐 Specification: SPEC-000.9.4 — DragonflyDB 2.0 Migration & Codebase-Wide State Audit

- **Status**: 🟡 **Proposed (Awaiting Ratification)**
- **Author**: Antigravity Platform Infrastructure & Distributed Systems Guild
- **Date**: 2026-09-22
- **Source Reference**: [`.histories/history61.txt`](file:///.histories/history61.txt), [`.histories/history62.txt`](file:///.histories/history62.txt) (Upgrade A — `UPGRADE-DF-2.0`)
- **Target Release**: Wallet Service V4 — Phase 000.9.4
- **Bounded Context**: Distributed State, Ingress Rate Limiting & Anti-Fraud Caching (`:fraud`, `:edge`, `:core`, Appliance Stack)
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly scoped to server upgrade, client compatibility, Lua script semantics, and state audit.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history61.txt`](file:///.histories/history61.txt): Identified Dragonfly 2.0 release (Sept 17, 2026). Mandated non-blind upgrade with 3 verification gates and codebase state audit inventory (`DF20-AUDIT`).
  - [`.histories/history62.txt`](file:///.histories/history62.txt): Refined invariants: vendor claims are benchmark targets, not invariants; formal continuous window math ($\text{Count}(W, t)$, $\text{Amount}(W, t)$); tenant scoping restricted to tenant-scoped state (`I-SEC-010`); emergency timeout ($\le 20\text{ms}$) separated from hot-path SLA; mandated dual-version behavioral oracle (`DragonflyBehavioralCompatibilityIT`) and zero DB thread blocking.
  - [`SPEC-000.1`](file:///.spec/SPEC-000.1-migrate-redis-to-dragonflydb.md): Initial migration from Redis to DragonflyDB 1.40 with dual UDS socket (`/var/run/redis/redis.sock`) and TCP fallback.
  - [`constitution.md`](file:///.agents/rules/constitution.md): `I-FRAUD-002` ($O(1)$ Hot Path, P99 $< 2\text{ms}$ at gateway, P99 $< 0.5\text{ms}$ in DragonflyDB).

---

## 1. Intent & Business Value

DragonflyDB powers hot-path velocity counters, tenant risk profiles, sliding windows, and rate limiters. Simply bumping the container tag from `1.40.1` to `2.0.x` ("blind upgrade") is strictly forbidden due to deep architectural dependencies on Lua atomicity, connection pooling, and TTL expiration semantics.
This specification establishes a **3-Gate Migration & Codebase-Wide State Audit Protocol**:
1. **Compatibility Gate**: Validate Lettuce client compatibility, RESP3 protocol negotiation, command syntax, and epoll UDS socket support.
2. **Behavioral Correctness Gate**: Audit and verify all Lua scripts, continuous sliding window counters, multi-tenant key namespacing (`I-SEC-010`), and TTL eviction behavior via a dual-version oracle (`Result_{1.40} \equiv Result_{2.0}`).
3. **Performance Regression/Benefit Gate**: Assert non-negative performance across throughput, latency, and memory, while measuring against target acceptance goals ($\ge 30\%$ memory reduction).
4. **DF20-AUDIT Improvement Inventory**: Classify and optimize every access pattern (Velocity, Risk Profile, Block Cache, Rate Limiter, Graph Cache), eliminating redundant round-trips and replacing sequential `GET`+`SET` pairs with atomic commands.

---

## 2. Mathematical & System Invariants

- **`I-DF20-001` (Hot-Path Latency Envelope)**: DragonflyDB read and write operations on the authorization hot path MUST complete within sub-millisecond bounds:
  $$P99(\text{Dragonfly}_{\text{UDS}}) < 0.5\text{ms}, \quad P99(\text{Dragonfly}_{\text{TCP}}) < 1.0\text{ms}$$
- **`I-DF20-002` (Zero-Regression Invariant & Target Gates)**: Dragonfly 2.0 MUST NOT regress throughput, latency, or memory efficiency against 1.40 under production-equivalent load:
  $$\text{Latency}_{P99}(DF_{2.0}) \le \text{Latency}_{P99}(DF_{1.40}), \quad \text{Throughput}(DF_{2.0}) \ge \text{Throughput}(DF_{1.40}), \quad \text{Mem}(DF_{2.0}) \le \text{Mem}(DF_{1.40})$$
  *(Benchmark Acceptance Targets: $\text{Mem}(DF_{2.0}) \le 0.70 \times \text{Mem}(DF_{1.40})$, $\text{Throughput}(DF_{2.0}) \ge 1.30 \times \text{Throughput}(DF_{1.40})$)*.
- **`I-DF20-003` (Atomic Continuous Sliding Window Consistency)**: Velocity evaluations MUST execute atomically over sorted continuous timestamp sets without window skew:
  $$\text{Count}(W, t) = |\{ \text{tx} \mid t - W < \text{occurredAt} \le t \}|, \quad \text{Amount}(W, t) = \sum_{t - W < \text{occurredAt}_i \le t} \text{amount}_i$$
- **`I-DF20-004` (Tenant Key Namespacing)**: Every tenant-scoped key written to or queried from DragonflyDB MUST include the cryptographically verified `tenantId` (`I-SEC-010`). Keys are explicitly partitioned:
  $$\forall k \in \text{Keys}_{\text{tenant}}, \quad k \in \{\text{"fraud:velocity:"} + \tau + ":" + u, \quad \text{"risk_profile:"} + \tau + ":" + e + ":" + i\}, \quad \tau = \text{tenantId}$$
  Global infrastructure keys (e.g. `system:version`, health probes) SHALL NOT require tenant scoping and are classified `GLOBAL`.
- **`I-DF20-005` (Emergency Timeout & Zero DB-Thread Blocking)**: Cache degradation timeout is bounded ($T_{\text{emergency}} \le 20\text{ms}$). Cache operations MUST NEVER block inside database transaction threads:
  $$\text{DBTxBlockedByCache} = 0, \quad \text{ThreadLeak}(\text{Fallback}) = 0$$

---

## 3. MoSCoW Requirements

### 3.1 Gate 1: Compatibility & Infrastructure [MUST]
- **`REQ-DF20-001` [MUST]**: Update `docker-compose.appliance.yaml` and Testcontainers to `docker.dragonflydb.io/dragonflydb/dragonfly:v2.0.x`.
- **`REQ-DF20-002` [MUST]**: Lettuce client configuration MUST negotiate RESP3 protocol and preserve epoll-based Unix Domain Socket transport (`/var/run/redis/redis.sock`) with TCP fallback (`6379`).
- **`REQ-DF20-003` [MUST]**: Validate all Redis commands used in `:fraud`, `:edge`, and `:core` against Dragonfly 2.0 command syntax (`HGETALL`, `HSET`, `EXPIRE`, `INCRBY`, `ZADD`, `ZREMRANGEBYSCORE`).

### 3.2 Gate 2: Behavioral Correctness & Oracle Verification [MUST]
- **`REQ-DF20-004` [MUST]**: Audit all Lua scripts (`RedisVelocityStore`, `GlobalVelocityRule`) to guarantee exact behavioral equivalence and deterministic integer/string conversions in Dragonfly 2.0.
- **`REQ-DF20-005` [MUST]**: Validate TTL expiration semantics: keys with millisecond/second TTLs MUST expire deterministically without ghost retention or premature eviction under memory pressure.
- **`REQ-DF20-006` [MUST]**: Verify tenant scoping across 100% of tenant-scoped Dragonfly state interactions (`I-DF20-004`, `I-SEC-010`).
- **`REQ-DF20-013` [MUST]**: Implement `DragonflyBehavioralCompatibilityIT` verifying that under identical inputs:
  $$\text{Result}_{DF1.40}(\text{op}) \equiv \text{Result}_{DF2.0}(\text{op})$$
  for Lua velocity, sliding windows, user blocks, TTL expiry, and tenant rate limiting.

### 3.3 Gate 3: DF20-AUDIT Improvement Inventory [SHOULD / COULD]
- **`REQ-DF20-007` [SHOULD]**: DF20-AUDIT Inventory: Audit and classify every state access pattern (Velocity, Risk Profile, Block Cache, Rate Limiter, Graph Cache) by Tenant-Scoped (Y/N), Atomic (Lua/command), Round Trips, and Optimization Grade (A: Optimal, B: Improvable, C: Redundant, D: Race-prone, E: Incorrectly scoped, F: Obsolete).
- **`REQ-DF20-008` [SHOULD]**: Eliminate redundant round-trips via pipelining and replace sequential `GET` followed by `SET` sequences with atomic equivalents (`GETSET`, `SET ... GET`) or local Caffeine caching.
- **`REQ-DF20-009` [COULD]**: Leverage Dragonfly 2.0 JSON / extended data structures for complex risk profiles if benchmark proves superior to hash fields.
- **`REQ-DF20-010` [COULD]**: Implement Dragonfly-backed distributed rate limiter token bucket fallback for multi-node Edge deployments.

### 3.4 Out of Scope [WON'T]
- **`REQ-DF20-011` [WON'T]**: Using DragonflyDB as persistent long-term storage (PostgreSQL remains single source of financial truth).
- **`REQ-DF20-012` [WON'T]**: Introducing Redis Cluster or Sentinel topology in V1 (Dragonfly vertical scaling on single appliance node fulfills requirements).

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Module | Affected Flow | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`fraud`** | Hot Risk Gate (`evaluateAuthorization`) | Cache miss or connection latency $>2\text{ms}$ | `I-DF20-001`: UDS socket connection ($P99 < 0.5\text{ms}$); fallback to deterministic rules. |
| **`fraud`** | Velocity Counter (`GlobalVelocityRule`) | Lua script failure or syntax change on 2.0 | `REQ-DF20-013`: Dual-version compatibility test suite validates equivalence. |
| **`edge`** | Perimeter Rate Limiting | Key collision between tenants | `I-DF20-004`: Enforce `RateLimitKey(tenantId, principalId)` prefixing. |
| **`ledger`** | Core Transaction | Out-of-sync blocklist cache | Dual-store sync event listener (`AccountBlockedEvent`) updates Dragonfly hash atomically. |
| **`ledger`** | Transaction Execution Thread | Cache hang blocks DB connection | `I-DF20-005`: Emergency timeout $\le 20\text{ms}$; zero DB transaction thread blocking. |

---

## 5. Deterministic Test Triads (`I-TDD-002`)

### Triad 1: Velocity Rule Atomic Continuous Evaluation (`REQ-DF20-004`, `REQ-DF20-013`)
- **Positive**: Sliding window accurately counts continuous transactions within duration $W$, returns `ALLOW` when sum $< \text{threshold}$.
- **Invalid Input**: Negative amount or zero-length duration rejected with `IllegalArgumentException`.
- **Boundary**: Exactly at $\text{threshold} + 1$, Lua script returns violation, triggering `REJECT` and persisting block state with exact parity across DF 1.40 and DF 2.0.

### Triad 2: Tenant Scoping vs. Global Infrastructure Isolation (`REQ-DF20-006`)
- **Positive**: Tenant `A` writes velocity counter for `user-1`; Tenant `B` queries same `user-1`; returns 0.
- **Global Key**: Health check queries `system:version` (global key); succeeds without requiring tenant context.
- **Boundary**: Attempting to write tenant-scoped entity without `tenantId` throws `TenantContextMissingException`.
