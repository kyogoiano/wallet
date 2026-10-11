# 📝 Task Breakdown: TASKS-000.03 — Deterministic Anti-Fraud Pre-Execution Gate & Velocity Rules

- **Associated Spec**: [`../SPEC-000.03-deterministic-anti-fraud-and-velocity-rules.md`](file:///.spec/SPEC-000.03-deterministic-anti-fraud-and-velocity-rules.md)
- **Associated Plan**: [`../plans/PLAN-000.03-deterministic-anti-fraud-and-velocity-rules.md`](file:///.spec/plans/PLAN-000.03-deterministic-anti-fraud-and-velocity-rules.md)
- **Status**: 🟢 **Completed & Verified**

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-FRD-001`, `I-FRAUD-004` | `UserBlockRuleTest.shouldTriggerWhenUserIsBlocked()` | `TASK-1.1`, `TASK-4.2` |
| `REQ-FRD-002`, `I-FRAUD-002` | `GlobalVelocityRuleTest.shouldTriggerWhenExceedingThreshold()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-FRD-003` | `SlidingWindowRuleTest.shouldTriggerWhenExceedingAmount()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-FRD-004` | `NewRecipientRuleTest.shouldTriggerOnRingTopology()` | `TASK-1.2` |
| `REQ-FRD-005`, `I-FRAUD-003` | `FraudReactionIT.shouldBlockAccountInPostgresWhenFraudDetected()` | `TASK-4.1`, `TASK-4.2` |
| `REQ-FRD-006`, `I-FRAUD-005` | `GlobalVelocityRuleTest.shouldNotTriggerOnReplay()` | `TASK-3.3` |
| `REQ-FRD-007` | `RedisUserStoreTest.shouldUseAsymmetricTtls()` | `TASK-3.4` |
| `REQ-FRD-008` | `FraudIT.shouldPublishFraudEventToOutbox()` | `TASK-4.3` |

---

## 2. Implementation Tasks

### Phase 1: Core Domain Rules & Accumulator [MUST]
- [x] `TASK-1.1`: Implement `RiskScore` accumulator with deterministic decision thresholds (`ALLOW` $< 40$, `REVIEW` $40 \le S < 80$, `BLOCK` $\ge 80$).
- [x] `TASK-1.2`: Implement `UserBlockRule` and `NewRecipientRule` domain evaluators.
- [x] `TASK-1.3`: Implement `FraudEngine.evaluate(...)` iterating all registered `FraudRule` components.

### Phase 2: Local Sliding Window & In-Memory Store [MUST]
- [x] `TASK-2.1`: Implement `SlidingAmountWindow` ring buffer array with second-level granularity and `LongAdder`.
- [x] `TASK-2.2`: Implement `SlidingWindowRule` checking volume against 10,000.00 limit.
- [x] `TASK-2.3`: Wire `LocalStateStore` backed by Caffeine cache for sliding windows.

### Phase 3: Redis Velocity Engine & Atomic Lua Script [MUST]
- [x] `TASK-3.1`: Author `VELOCITY_SCRIPT` Lua script with `zadd NX`, `zremrangebyscore`, and auto-expiry.
- [x] `TASK-3.2`: Implement `RedisVelocityStore.checkVelocity(...)` returning `VelocityResult` (Ok, Exceeded, Replay).
- [x] `TASK-3.3`: Handle `VelocityResult.Replay` yielding 0 score points in `GlobalVelocityRule`.
- [x] `TASK-3.4`: Implement `RedisUserStore` with asymmetric cache expiry (10 min blocked, 30 sec active).

### Phase 4: Pre-Execution Gate Orchestration & Dual-Store Sync [MUST]
- [x] `TASK-4.1`: Implement `FraudCheckHelper.performFraudCheck(...)` executing ahead of ledger use case transactions.
- [x] `TASK-4.2`: Implement dual-store blocking: updating `accounts.status = 'BLOCKED'` in PostgreSQL and setting Redis block flag.
- [x] `TASK-4.3`: Persist `FraudEvent` to `outbox` table for downstream auditing.
