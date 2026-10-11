# 📐 Specification: SPEC-000.9.4 — DragonflyDB 2.0 Migration & Codebase-Wide State Audit

- **Status**: 🟢 **Ratified (Rev. 1 per History 69)**
- **Author**: Antigravity Platform Infrastructure & Distributed Systems Guild
- **Date**: 2026-09-22
- **Source Reference**: [`.histories/history61.txt`](file:///.histories/history61.txt), [`.histories/history62.txt`](file:///.histories/history62.txt), [`.histories/history69.txt`](file:///.histories/history69.txt)
- **Target Release**: Wallet Service V4 — Phase 000.9.4
- **Bounded Context**: Distributed State, Ingress Rate Limiting & Anti-Fraud Caching (`:fraud`, `:edge`, `:core`, Appliance Stack)
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly scoped to server upgrade, client compatibility, Lua script semantics, state audit, and non-blocking resilience.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history61.txt`](file:///.histories/history61.txt): Identified Dragonfly 2.0 release. Mandated non-blind upgrade with verification gates and state audit (`DF20-AUDIT`).
  - [`.histories/history62.txt`](file:///.histories/history62.txt): Vendor claims are benchmark targets; continuous window math; tenant key scoping (`I-SEC-010`); emergency timeout bounded to $\le 20\text{ms}$; dual-version oracle.
  - [`.histories/history69.txt`](file:///.histories/history69.txt): 3-Gate + Cross-Cutting DF20-AUDIT structure; decoupled operational non-regression from benchmark targets; semantic normalized equivalence ($\equiv$); pipelining $\ne$ atomicity; TTL expiration vs memory eviction semantics; cache-before-transaction ordering; in-flight boundedness.
  - [`constitution.md`](file:///.agents/rules/constitution.md): `I-FRAUD-002` ($O(1)$ Hot Path, Fraud Gate $P99 < 2.0\text{ms}$), `I-ATOMICITY-001` (Single DB transaction boundary).

---

## 1. Intent & Business Value

DragonflyDB powers hot-path velocity counters, tenant risk profiles, sliding windows, and rate limiters. A "blind upgrade" (bumping container tags without verification) is strictly forbidden due to deep architectural dependencies on Lua atomicity, connection pooling, and TTL expiration semantics.
This specification establishes a **3-Gate Migration & Cross-Cutting DF20-AUDIT Protocol**:
1. **Gate 1 (Compatibility & Infrastructure)**: Validate Lettuce RESP3 protocol negotiation, command set syntax, and epoll UDS socket transport.
2. **Gate 2 (Behavioral Correctness & Dual Oracle)**: Verify Lua scripts, continuous sliding window counters, TTL expiry, and tenant key isolation via a dual-version oracle asserting normalized semantic equivalence ($\text{Result}_{1.40} \equiv \text{Result}_{2.0}$).
3. **Gate 3 (Performance & Non-Regression)**: Verify zero statistically significant performance regression against 1.40 baseline while measuring against vendor efficiency targets ($\ge 30\%$ memory reduction).
4. **Cross-Cutting `DF20-AUDIT` Inventory**: Exhaustive audit and classification of 100% of codebase Dragonfly interactions, fixing all un-namespaced keys and race-prone access patterns.

---

## 2. Mathematical & System Invariants

- **`I-DF20-001` (Hot-Path Latency Envelope)**: DragonflyDB read and write operations under the V4 appliance profile MUST complete within component latency envelopes, strictly bounded upstream by Fraud Gate SLA `I-FRAUD-002` ($P99 < 2.0\text{ms}$):
  $$P99(\text{Dragonfly}_{\text{UDS}}) < 0.5\text{ms}, \quad P99(\text{Dragonfly}_{\text{TCP}}) < 1.0\text{ms}$$
- **`I-DF20-002` (Non-Regression Against Baseline & Target Goals)**: Dragonfly 2.0 MUST NOT exhibit statistically significant performance regression against 1.40 under identical production workloads:
  $$\text{Latency}_{P99}(DF_{2.0}) \le \text{Latency}_{P99}(DF_{1.40}), \quad \text{Throughput}(DF_{2.0}) \ge \text{Throughput}(DF_{1.40}), \quad \text{Mem}(DF_{2.0}) \le \text{Mem}(DF_{1.40})$$
  *(Optimization Targets: $\text{Mem}(DF_{2.0}) \le 0.70 \times \text{Mem}(DF_{1.40})$, $\text{Throughput}(DF_{2.0}) \ge 1.30 \times \text{Throughput}(DF_{1.40})$)*.
- **`I-DF20-003` (Atomic Continuous Sliding Window Consistency)**: Velocity evaluations MUST execute atomically over sorted continuous timestamp sets without window skew:
  $$\text{Count}(W, t) = |\{ \text{tx} \mid t - W < \text{occurredAt} \le t \}|, \quad \text{Amount}(W, t) = \sum_{t - W < \text{occurredAt}_i \le t} \text{amount}_i$$
- **`I-DF20-004` (Canonical Tenant Key Namespacing)**: Every tenant-scoped key written to DragonflyDB MUST include the canonical `tenantId` (`I-SEC-010`). Missing tenant context throws `TenantContextMissingException`:
  $$\forall k \in \text{Keys}_{\text{tenant}}, \quad k \in \{\text{"fraud:velocity:"} + \tau + ":" + u, \quad \text{"risk_profile:"} + \tau + ":" + e + ":" + i\}, \quad \tau = \text{tenantId}$$
  Global infrastructure keys (e.g. `system:version`) SHALL NOT require tenant scoping and are explicitly classified `GLOBAL`.
- **`I-DF20-005` (Emergency Timeout & Zero DB-Thread Blocking)**: Cache degradation timeout is bounded ($T_{\text{emergency}} \le 20\text{ms}$). Cache operations MUST NEVER block inside database transaction threads:
  $$\text{DBTxBlockedByCache} = 0, \quad \text{ThreadLeak}(\text{Fallback}) = 0$$
- **`I-DF20-006` (Cache Degradation & Non-Authoritative Semantics)**: DragonflyDB is strictly ephemeral and non-authoritative. A cache miss or evicted entry MUST degrade to deterministic rule evaluation and MUST NEVER be implicitly interpreted as an authorization to bypass fraud checks.
- **`I-DF20-007` (Global Replay Key Uniqueness Contract)**: Global replay protection keys (`fraud:op:{operationId}`) MUST use cryptographically random, globally unique UUID/ULID values (`I-SEC-006`). Global keys are strictly prohibited from holding tenant-specific business state without explicit namespacing.

---

## 3. MoSCoW Requirements

### 3.1 Gate 1: Compatibility & Infrastructure [MUST]
- **`REQ-DF20-001` [MUST]**: Update `docker-compose.appliance.yaml`, `docker-compose.yaml`, and Testcontainers to `docker.dragonflydb.io/dragonflydb/dragonfly:v2.0.x`.
- **`REQ-DF20-002` [MUST]**: Lettuce client configuration MUST negotiate RESP3 protocol and preserve epoll-based Unix Domain Socket transport (`/var/run/redis/redis.sock`) with TCP fallback (`6379`).
- **`REQ-DF20-003` [MUST]**: Validate all Redis commands used in `:fraud`, `:edge`, and `:core` against Dragonfly 2.0 command syntax (`HGETALL`, `HSET`, `EXPIRE`, `INCRBY`, `ZADD`, `ZREMRANGEBYSCORE`, `ZCARD`, `PSETEX`, `PEXPIRE`, `DEL`, `SADD`, `SCARD`).

### 3.2 Gate 2: Behavioral Correctness & Oracle Verification [MUST]
- **`REQ-DF20-004` [MUST]**: Audit all Lua scripts (`RedisVelocityStore`, `RedisScripts`, `NewRecipientStore`) to guarantee exact behavioral equivalence and deterministic integer/string conversions in Dragonfly 2.0.
- **`REQ-DF20-005` [MUST]**: Validate TTL expiration vs. memory eviction semantics: keys with TTL expire deterministically without ghost retention; eviction under memory pressure triggers safe degradation (`I-DF20-006`).
- **`REQ-DF20-006` [MUST]**: Verify tenant scoping across 100% of tenant-scoped Dragonfly state interactions (`I-DF20-004`, `REQ-DF20-016`).
- **`REQ-DF20-013` [MUST]**: Implement `DragonflyBehavioralCompatibilityIT` verifying normalized semantic equivalence ($\equiv$), not byte-for-byte wire equality:
  $$\text{Normalize}(\text{Result}_{DF1.40}(\text{op})) \equiv \text{Normalize}(\text{Result}_{DF2.0}(\text{op}))$$
- **`REQ-DF20-014` [MUST]**: **Cache-Before-Transaction Ordering**: All Dragonfly operations used for authorization/risk pre-check MUST complete before acquisition of the database transaction (`SELECT FOR UPDATE`). No Dragonfly I/O may occur inside the DB transaction.
- **`REQ-DF20-015` [MUST]**: **Timeout & In-Flight Boundedness**: Emergency timeout ($T_{\text{emergency}} \le 20\text{ms}$) MUST bound request-side waiting and cancel or bound underlying in-flight operations without leaking threads or Netty buffers.
- **`REQ-DF20-016` [MUST]**: **Canonical Tenant Key Encoding**: Enforce `SPEC-000.9.3` canonical encoding (`tenantId <= 64`). Raw/unvalidated tenant identifiers MUST NOT be concatenated into Dragonfly keys.

### 3.3 Gate 3: Performance & Non-Regression [MUST]
- **`REQ-DF20-017` [MUST]**: Benchmark harness validating that Dragonfly 2.0 exhibits zero statistically significant regression in P99 latency, throughput, and memory under 10,000 mixed ops/sec across 16 threads (`I-DF20-002`).

### 3.4 Cross-Cutting: DF20-AUDIT Improvement Inventory [MUST / SHOULD / COULD]
- **`REQ-DF20-007` [MUST]**: Exhaustive State Inventory: Audit and classify 100% of codebase Dragonfly access patterns (Velocity, Risk Profile, User Block, Replay, Recipients, Graph Risk) with quality grades (A-F) and Authority classification (Non-Authoritative vs Authoritative). Rectify all Grade E (un-namespaced) and Grade D (race-prone) defects.
- **`REQ-DF20-008` [SHOULD]**: Eliminate redundant round-trips where semantics permit. Pipelining MAY be used for batching independent operations, but pipelining alone MUST NOT be treated as a guarantee of atomic read-modify-write execution.
- **`REQ-DF20-009` [COULD]**: Leverage Dragonfly 2.0 JSON / extended data structures for complex risk profiles if benchmark proves superior to hash fields.
- **`REQ-DF20-010` [COULD]**: Implement Dragonfly-backed distributed rate limiter token bucket fallback for multi-node Edge deployments.

### 3.5 Out of Scope [WON'T]
- **`REQ-DF20-011` [WON'T]**: Using DragonflyDB as persistent long-term storage (PostgreSQL remains single source of financial truth).
- **`REQ-DF20-012` [WON'T]**: Introducing Redis Cluster or Sentinel topology in V1 (Dragonfly vertical scaling on single appliance node fulfills requirements).

---

## 4. Gate Promotion Criteria

Promotion from Dragonfly 1.40 to 2.0 requires unanimous green gates:
$$\text{PROMOTION} \iff \text{Gate 1 (Compatibility)} = \text{PASS} \land \text{Gate 2 (Oracle \& Tenant Audit)} = \text{PASS} \land \text{Gate 3 (Non-Regression)} = \text{PASS}$$

---

## 5. Cross-Feature Impact Matrix (`I-SDD-005`)

| Module | Affected Flow | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`fraud`** | Hot Risk Gate (`evaluateAuthorization`) | Cache miss or connection latency $>2\text{ms}$ | `I-DF20-001`: UDS socket connection ($P99 < 0.5\text{ms}$); fallback to deterministic rules (`I-DF20-006`). |
| **`fraud`** | Velocity Counter (`GlobalVelocityRule`) | Lua script failure or syntax change on 2.0 | `REQ-DF20-013`: Dual-version compatibility test suite validates equivalence. |
| **`edge`** | Perimeter Rate Limiting | Key collision between tenants | `I-DF20-004`, `REQ-DF20-016`: Enforce canonical `RateLimitKey(tenantId, principalId)`. |
| **`ledger`** | Core Transaction | Out-of-sync blocklist cache | Dual-store sync event listener updates Dragonfly hash atomically. |
| **`ledger`** | Transaction Execution Thread | Cache hang blocks DB connection | `I-DF20-005`, `REQ-DF20-014`: Cache-before-transaction ordering; $T_{\text{emergency}} \le 20\text{ms}$. |

---

## 6. Deterministic Test Triads (`I-TDD-002`)

### Triad 1: Velocity Rule Atomic Continuous Evaluation (`REQ-DF20-004`, `REQ-DF20-013`, `I-DF20-003`)
- **Count & Amount Tests**:
  - Positive Count: $\text{count} \le \text{threshold}_{\text{count}} \implies \text{ALLOW}$; $\text{count} > \text{threshold}_{\text{count}} \implies \text{REJECT}$.
  - Positive Amount: $\text{amount} \le \text{threshold}_{\text{amount}} \implies \text{ALLOW}$; $\text{amount} > \text{threshold}_{\text{amount}} \implies \text{REJECT}$.
- **Boundary Semantics**: Explicit temporal boundaries evaluated for $W$:
  $$t - W \implies \text{excluded}, \quad t - W + \epsilon \implies \text{included}, \quad t \implies \text{included}, \quad t + \epsilon \implies \text{excluded}$$
- **Invalid Input**: Negative amount, zero window duration, or null tenant ID throws `IllegalArgumentException`.

### Triad 2: Canonical Tenant Scoping vs. Global Key Isolation (`REQ-DF20-006`, `REQ-DF20-016`, `I-DF20-004`)
- **Positive**: Tenant `tenant-alpha` writes velocity counter for `user-1`; Tenant `tenant-beta` queries `user-1`; returns 0.
- **Global Key**: Health check queries `system:version`; succeeds without requiring tenant context. Replay key `fraud:op:{uuid}` verified globally unique.
- **Boundary**: Attempting to write tenant-scoped entity without `tenantId` throws `TenantContextMissingException`.

### Triad 3: Emergency Timeout & Non-Blocking Database Transaction Boundary (`I-DF20-005`, `REQ-DF20-014`, `REQ-DF20-015`)
- **Positive**: Dragonfly responds in $<0.5\text{ms}$; domain use case proceeds to execute database transaction.
- **Boundary**: Simulated cache network partition causes Lettuce command to exceed $T_{\text{emergency}} = 20\text{ms}$; request immediately aborts cache lookup and degrades to local Caffeine rules. Zero database transaction threads (`SELECT FOR UPDATE`) are held or blocked. In-flight operations cancelled without thread leakage.

### Triad 4: Dual-Version Normalized Semantic Oracle (`REQ-DF20-013`)
- **Positive**: For identical multi-key Lua scripts and complex data types, Dragonfly 2.0 returns normalized semantic equivalence to Dragonfly 1.40.1 ($\text{Normalize}(R_{1.40}) \equiv \text{Normalize}(R_{2.0})$).
- **Boundary**: Rapid sequential ZSET eviction under high clock drift maintains mathematical invariant $\text{Count}(W, t)$ without ghost retention.
