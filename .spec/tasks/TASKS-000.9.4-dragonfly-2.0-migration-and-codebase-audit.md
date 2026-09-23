# 📝 Task Breakdown: TASKS-000.9.4 — DragonflyDB 2.0 Migration & Codebase State Audit

- **Associated Spec**: [`../SPEC-000.9.4-dragonfly-2.0-migration-and-codebase-audit.md`](file:///.spec/SPEC-000.9.4-dragonfly-2.0-migration-and-codebase-audit.md)
- **Associated Architecture**: [`../architecture/ARCH-000.9.4-dragonfly-2.0-and-state-audit.md`](file:///.spec/architecture/ARCH-000.9.4-dragonfly-2.0-and-state-audit.md)
- **Associated Plan**: [`../plans/PLAN-000.9.4-dragonfly-2.0-migration-and-codebase-audit.md`](file:///.spec/plans/PLAN-000.9.4-dragonfly-2.0-migration-and-codebase-audit.md)
- **Status**: 🟢 **Ready for Implementation (Rev. 1 per History 70)**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant / Triad | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-DF20-001`, `REQ-DF20-002`, `REQ-DF20-003` | `[MUST]` | `DragonflyCompatibilityTest` | `TASK-DF20-1.1`, `TASK-DF20-1.2`, `TASK-DF20-1.3`, `TASK-DF20-1.4` |
| `REQ-DF20-004`, `I-DF20-003`, Triad 1 | `[MUST]` | `DragonflyBehavioralCompatibilityIT` | `TASK-DF20-2.1`, `TASK-DF20-2.3`, `TASK-DF20-3.2` |
| `REQ-DF20-005`, `I-DF20-006` | `[MUST]` | `DragonflyBehavioralCompatibilityIT`, `CacheTransactionIsolationTest` | `TASK-DF20-3.2`, `TASK-DF20-3.3` |
| `REQ-DF20-006`, `REQ-DF20-016`, `I-DF20-004`, Triad 2 | `[MUST]` | `TenantStateIsolationTest` | `TASK-DF20-2.1`, `TASK-DF20-2.2`, `TASK-DF20-2.4`, `TASK-DF20-2.5` |
| `REQ-DF20-007` | `[MUST]` | `TenantStateIsolationTest`, `DF20AuditVerificationTest` | `TASK-DF20-2.1`, `TASK-DF20-2.2`, `TASK-DF20-2.3`, `TASK-DF20-2.4` |
| `I-DF20-007` | `[MUST]` | `TenantStateIsolationTest` | `TASK-DF20-2.3`, `TASK-DF20-2.5` |
| `REQ-DF20-013`, Triad 4 | `[MUST]` | `DragonflyBehavioralCompatibilityIT` | `TASK-DF20-3.1`, `TASK-DF20-3.2` |
| `REQ-DF20-014`, `REQ-DF20-015`, `I-DF20-005`, Triad 3 | `[MUST]` | `CacheTransactionIsolationTest`, `DragonflyArchitecturalBoundaryTest` | `TASK-DF20-3.3`, `TASK-DF20-5.1` |
| `REQ-DF20-017`, `I-DF20-001`, `I-DF20-002` | `[MUST]` | `DragonflyBenchmarkTest` | `TASK-DF20-4.1` |
| Gate Promotion Criteria | `[MUST]` | `GatePromotionReportTest` ($G_1 \land G_2 \land G_3$) | `TASK-DF20-5.2` |
| `REQ-DF20-008` | `[SHOULD]` | `RedisRiskProfileStorePipeliningTest` | `TASK-DF20-2.2`, `TASK-DF20-6.1` |
| `REQ-DF20-009` | `[COULD]` | `DragonflyJsonProfilePrototypeTest` | `TASK-DF20-6.2` |
| `REQ-DF20-010` | `[COULD]` | `DistributedTokenBucketTest` | `TASK-DF20-6.3` |
| `REQ-DF20-011`, `REQ-DF20-012` | `[WON'T]` | `DragonflyArchitecturalBoundaryTest` (PostgreSQL single authority, no cluster) | `TASK-DF20-5.1` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing a task, focus strictly on the active task card below. Do not load unrelated modules into memory.

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Gate 1 — Container Infrastructure & Lettuce Client Upgrade
- [x] `TASK-DF20-1.1` [MUST]: Upgrade Docker Compose and appliance manifests (`docker-compose.yaml`, `docker-compose.appliance.yaml`) to `docker.dragonflydb.io/dragonflydb/dragonfly:v2.0.x` (`REQ-DF20-001`):
  - Configure UDS ownership and permissions so that the authorized Wallet/Dragonfly client process can access `/var/run/redis/redis.sock` without broadening permissions beyond the appliance threat model.
  - Maintain `--proactor_threads=2`, `--maxmemory=512mb` (standard) / `768mb` (appliance), and `--cache_mode=true`.
- [x] `TASK-DF20-1.2` [MUST]: Update test infrastructure in `IntegrationTestBase.java` to support `dragonfly:v2.0.x` as default and enable dual-container capabilities for behavioral testing (`REQ-DF20-001`).
- [x] `TASK-DF20-1.3` [MUST]: Configure Lettuce client in `RedisConfig.java` (`REQ-DF20-002`, `REQ-DF20-015`):
  - Negotiate `ProtocolVersion.RESP3`.
  - Configure socket options with 2s connect timeout.
  - Enforce bounded emergency cache timeout ($T_{\text{emergency}} \le 20\text{ms}$) via command timeout options. After timeout, implementation MUST NOT create unbounded in-flight fallback work or thread/task accumulation. The test MUST demonstrate zero thread/task leakage under repeated timeout conditions (`REQ-DF20-015`).
- [x] `TASK-DF20-1.4` [MUST]: Implement `DragonflyCompatibilityTest` (Gate 1 Verification):
  - Verify RESP3 protocol negotiation, Unix Domain Socket connection with TCP fallback, and standard Redis command parsing (`HGETALL`, `HSET`, `EXPIRE`, `INCRBY`, `ZADD`, `ZREMRANGEBYSCORE`, `ZCARD`, `PSETEX`, `PEXPIRE`, `DEL`, `SADD`, `SCARD`).

### Phase 2: Cross-Cutting — `DF20-AUDIT` Key Namespacing & Store Refactoring
- [x] `TASK-DF20-2.1` [MUST]: Refactor `RedisVelocityStore.java` to mandate canonical `tenantId` (`REQ-DF20-004`, `REQ-DF20-007`, `REQ-DF20-016`):
  - Key pattern: `fraud:velocity:{tenantId}:{userId}`.
  - Method signature: `recordTransaction(String tenantId, UUID userId, Instant timestamp, Duration window)`.
  - Audit Lua script to verify sorted set `zadd NX`, `zremrangebyscore`, `zcard`, and `pexpire` execute atomically in Dragonfly 2.0.
- [x] `TASK-DF20-2.2` [MUST]: Refactor `RedisRiskProfileStore.java` (`REQ-DF20-007`, `REQ-DF20-008`, `REQ-DF20-016`):
  - Key pattern: `risk_profile:{tenantId}:USER:{id}`.
  - Determine whether `HSET` + `EXPIRE` requires transactional atomicity. If atomicity is required, use a single atomic command or Lua script; if atomicity is not required for profile initialization, pipelining is permitted as a round-trip optimization (`REQ-DF20-008`).
- [x] `TASK-DF20-2.3` [MUST]: Refactor `RedisScripts.java` (`REVIEW_COUNT_PROTECTED_SCRIPT` and `BLOCK_PROTECTED_SCRIPT`) (`REQ-DF20-004`, `REQ-DF20-007`, `I-DF20-007`):
  - Refactor keys: `user:{tenantId}:{userId}:review_count`, `user:{tenantId}:{userId}:risk_score`, `user:{tenantId}:{userId}:blocked`.
  - Enforce global replay protection contract: `KEYS[1]` (`fraud:op:{operationId}`) MUST require globally unique UUID/ULID values (`I-DF20-007`).
- [x] `TASK-DF20-2.4` [MUST]: Refactor `NewRecipientStore.java` and Graph hot cache keys (`REQ-DF20-007`, `REQ-DF20-016`):
  - `NewRecipientStore`: `tenant:{tenantId}:recipient:{userId}` and `tenant:{tenantId}:sender:{userId}`.
  - Graph hot state: `risk:{tenantId}:user:{userId}:graph_risk` and `risk:{tenantId}:user:{userId}:temporal_risk`.
- [x] `TASK-DF20-2.5` [MUST]: Implement `TenantStateIsolationTest` (Test Triad 2):
  - Positive: Tenant `tenant-alpha` writes velocity counter for `user-1`; Tenant `tenant-beta` queries `user-1`; returns 0.
  - Global Key: Health probe queries `system:version`; succeeds without requiring tenant context. Replay key `fraud:op:{uuid}` verified globally unique (`I-DF20-007`).
  - Boundary: Attempting to write tenant-scoped entity without canonical `tenantId` throws `TenantContextMissingException`.

### Phase 3: Gate 2 — Behavioral Correctness & Dual-Version Oracle
- [x] `TASK-DF20-3.1` [MUST]: Implement `DragonflyOutputNormalizer` utility (`REQ-DF20-013`):
  - Normalize hash field ordering and internal representation variations.
  - Compare residual TTL within an explicit measurement tolerance ($\Delta_{\text{clock}} \le 100\text{ms}$) rather than ignoring variations, failing if drift exceeds the declared boundary.
- [x] `TASK-DF20-3.2` [MUST]: Implement `DragonflyBehavioralCompatibilityIT` (Test Triad 1 & Triad 4):
  - Spawn both `dragonfly:v1.40.1` and `dragonfly:v2.0.x` Testcontainers.
  - Test Count and Amount threshold boundaries: $\text{count} \le \text{threshold}_{\text{count}} \implies \text{ALLOW}$; $\text{count} > \text{threshold}_{\text{count}} \implies \text{REJECT}$.
  - Test explicit temporal sliding window boundaries: $t - W$ (excluded), $t - W + \epsilon$ (included), $t$ (included), $t + \epsilon$ (excluded).
  - Test zero-event window ($W$ empty $\implies \text{count}=0, \text{amount}=0$) and expired-event window (stale members evicted $\implies \text{count}=0, \text{amount}=0$).
  - Parity for `REVIEW_COUNT_PROTECTED_SCRIPT`, `BLOCK_PROTECTED_SCRIPT`, and `NewRecipientStore`.
  - Deterministic second and millisecond TTL expiration.
  - Assert normalized semantic equivalence: $\text{Normalize}(\text{Result}_{1.40}) \equiv \text{Normalize}(\text{Result}_{2.0})$.
- [x] `TASK-DF20-3.3` [MUST]: Implement `CacheTransactionIsolationTest` (Test Triad 3):
  - Assert strict cache-before-transaction ordering (`REQ-DF20-014`).
  - Assert non-authoritative degradation on cache miss or eviction falls back safely to deterministic rules without bypassing fraud gates (`I-DF20-006`).
  - Repeated timeout test: simulate $N$ repeated timeouts and partitions; verify bounded resources, zero thread accumulation, and `ThreadLeak(Fallback) = 0` (`I-DF20-005`, `REQ-DF20-015`). Zero database transaction threads (`SELECT FOR UPDATE`) are held or blocked.

### Phase 4: Gate 3 — Performance & Non-Regression Benchmark
- [x] `TASK-DF20-4.1` [MUST]: Implement `DragonflyBenchmarkTest` (Gate 3 Verification, `REQ-DF20-017`):
  - Execute multi-iteration benchmark (warm-up runs followed by repeated measurement runs) using deterministic workload vectors (fixed random seed, 100k active keys, 70/30 read/write ratio, identical timestamp/amount distribution) across 16 concurrent virtual threads against both Dragonfly 1.40 and 2.0.
  - Report P50, P95, P99, throughput, RSS memory, and error rate.
  - Assert component latency envelope: $P99(\text{UDS}) < 0.5\text{ms}$, $P99(\text{TCP}) < 1.0\text{ms}$ (`I-DF20-001`).
  - Assert zero statistically significant performance regression: $P99(DF_{2.0}) \le P99(DF_{1.40})$ (`I-DF20-002`).
  - Record progress against optimization targets ($\ge 30\%$ throughput increase, $\ge 30\%$ memory reduction).

### Phase 5: Architectural Boundary & Promotion Verification
- [x] `TASK-DF20-5.1` [MUST]: Implement `DragonflyArchitecturalBoundaryTest`:
  - Static ArchUnit check: assert no class in `br.com.wallet.ledger` packages depends on Redis/Dragonfly infrastructure classes (`REQ-DF20-014`).
  - Runtime integration check: spy/instrument Dragonfly client to assert zero Redis calls occur during active `@Transactional` domain transfer execution (`REQ-DF20-014`).
  - Assert Dragonfly is never treated as authoritative source of truth for financial balances (`I-LEDGER-001`).
- [x] `TASK-DF20-5.2` [MUST]: Implement `GatePromotionReportTest`:
  - Generate a structured `GatePromotionReport` artifact capturing Gate 1, Gate 2, and Gate 3 metrics, mathematically asserting:
    $$\text{Promotion} \iff \text{Gate 1 (Compatibility)} = \text{PASS} \land \text{Gate 2 (Oracle)} = \text{PASS} \land \text{Gate 3 (Non-Regression)} = \text{PASS}$$

### Phase 6: Optional Performance Optimizations (`[SHOULD]` / `[COULD]`)
- [ ] `TASK-DF20-6.1` [SHOULD]: Profile and eliminate redundant lookups across `FraudGate` via local Caffeine pre-warming (`REQ-DF20-008`).
- [ ] `TASK-DF20-6.2` [COULD]: Prototype Dragonfly 2.0 JSON native document evaluation for behavioral profiles (`REQ-DF20-009`).
- [ ] `TASK-DF20-6.3` [COULD]: Implement distributed token bucket rate limiter fallback using Dragonfly 2.0 atomic commands for multi-node Edge deployments (`REQ-DF20-010`).
