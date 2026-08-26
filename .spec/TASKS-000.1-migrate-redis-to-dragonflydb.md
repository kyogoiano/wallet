# 📝 Task Breakdown: TASKS-000.1 — In-Memory Store Migration: Redis to DragonflyDB

- **Associated Spec**: [`SPEC-000.1-migrate-redis-to-dragonflydb.md`](file:///.spec/SPEC-000.1-migrate-redis-to-dragonflydb.md)
- **Associated Plan**: [`PLAN-000.1-migrate-redis-to-dragonflydb.md`](file:///.spec/PLAN-000.1-migrate-redis-to-dragonflydb.md)
- **Status**: Completed / Verified

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-DF-001` | Docker Compose inspection & healthcheck | `TASK-1.1`, `TASK-1.2` |
| `REQ-DF-002` | `RedisConfig` UDS and TCP test connections | `TASK-2.1` |
| `REQ-DF-003` | `DragonflyLuaCompatibilityIT.shouldExecuteReviewScriptAndAccumulateRisk` | `TASK-3.1` |
| `REQ-DF-004` | `DragonflyLuaCompatibilityIT.shouldExecuteBlockScriptIdempotently` | `TASK-3.2` |
| `REQ-DF-005` | `FraudIT`, `FraudReactionIT`, `GlobalVelocityRuleTest` | `TASK-4.1` |
| `REQ-DF-006` | `IntegrationTestBase` Dragonfly Testcontainer startup | `TASK-2.2` |
| `I-DF-001` | Full Integration Test Suite | `TASK-4.2` |
| `I-DF-002` | `DragonflyLuaCompatibilityIT.shouldHandleConcurrentScriptExecutionsWithoutDeadlock` | `TASK-3.3` |
| `I-DF-004` | `DragonflyLuaCompatibilityIT.shouldDetectReplayInReviewScript` | `TASK-3.1` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Test Infrastructure & Testcontainers Setup
- [x] `TASK-1.1`: Update `IntegrationTestBase.java` to use `docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1` for Testcontainers in-memory caching.
- [x] `TASK-1.2`: Verify Testcontainers boots DragonflyDB with port 6379 mapped and healthy.

### Phase 2: Dedicated Lua Compatibility Test Suite
- [x] `TASK-2.1` [RED]: Create `DragonflyLuaCompatibilityIT` testing `REVIEW_COUNT_PROTECTED_SCRIPT` (risk accumulation, review thresholds, replay protection).
- [x] `TASK-2.2` [RED]: Add test cases for `BLOCK_PROTECTED_SCRIPT` (idempotent user blocking, optional TTL, replay protection).
- [x] `TASK-2.3` [RED]: Add concurrency and multi-threaded stress test executing concurrent Lua script evaluations across multiple threads.

### Phase 3: Docker Compose & Runtime Configuration
- [x] `TASK-3.1`: Update `docker-compose.yaml` replacing Redis container with `docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1` with `--unixsocket=/var/run/redis/redis.sock`, `--unixsocketperm=777`, `--maxmemory=512mb`, and `--cache_mode=true`.
- [x] `TASK-3.2`: Update `docker/init` / environment configs if applicable.

### Phase 4: Full Regression & Modulith Convergence
- [x] `TASK-4.1`: Execute all fraud, account blocking, and ledger integration tests (`FraudIT`, `FraudReactionIT`, `AccountBlockingIT`, `BalanceIT`, `WithdrawFundsIT`, `TransferFundsIT`).
- [x] `TASK-4.2`: Verify `ModulithArchitectureTest.verifyArchitecture()` passes with 0 violations.
- [x] `TASK-4.3`: Author `SUMMARY-000.1-migrate-redis-to-dragonflydb.md` upon completion.

---

## 3. Convergence & Verification Checklist

- [ ] DragonflyDB running in Testcontainers and `docker-compose.yaml`
- [ ] All Lua scripts validated against Dragonfly's multi-key lock manager
- [ ] Replay protection (`I-DF-004`) verified under concurrency
- [ ] Line coverage meets requirements ($\ge 70\%$ overall, $\ge 85\%$ core/fraud)
- [ ] Zero Modulith architecture violations
