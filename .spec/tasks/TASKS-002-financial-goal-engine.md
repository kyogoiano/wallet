# 📝 Task Breakdown: TASKS-002 — Financial Goal & Cashflow Strategy Engine (br.com.wallet.goals)

- **Associated Spec**: [`../SPEC-002-financial-goal-engine.md`](file:///.spec/SPEC-002-financial-goal-engine.md)
- **Associated Plan**: [`../plans/PLAN-002-financial-goal-engine.md`](file:///.spec/plans/PLAN-002-financial-goal-engine.md)
- **Status**: Ready for TDD Implementation

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-GOAL-001`, `REQ-GOAL-002`, `REQ-GOAL-003`, `REQ-GOAL-004` | `FinancialGoalServiceTest.java` | `TASK-2.1`, `TASK-2.2` |
| `REQ-GOAL-005` | `GoalQueryServiceTest.java` | `TASK-2.3` |
| `REQ-GOAL-006`, `REQ-GOAL-007` | `CashflowProfileServiceTest.java`, `CashflowProfileDaoTest.java` | `TASK-2.4`, `TASK-3.2` |
| `REQ-GOAL-008`, `REQ-GOAL-009`, `REQ-GOAL-010` | `GoalStrategyEngineTest.java`, `ContributionCalculatorTest.java` | `TASK-1.1`, `TASK-1.2` |
| `REQ-GOAL-011` | `GoalStrategyEngineTest.java` (Waterfall tests) | `TASK-1.3` |
| `REQ-GOAL-012` | `GoalStrategyServiceTest.java`, `GoalControllerTest.java` | `TASK-2.5`, `TASK-4.1` |
| `I-GOAL-001` | `ModulithArchitectureTest.java` | `TASK-5.1` |
| `I-GOAL-002` | `CashflowCapacityCalculatorTest.java` | `TASK-1.2` |
| `I-GOAL-003`, `I-GOAL-004` | `GoalStrategyEngineTest.java` | `TASK-1.1` |
| `I-GOAL-005` | `ContributionCalculatorTest.java` | `TASK-1.2` |
| `I-GOAL-006` | `GoalStrategyEngineTest.java` | `TASK-1.3` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Engine & Domain Models (Pure Stateless Math)
- [x] `TASK-1.1` [RED]: Write unit tests for `ContributionCalculator`, `CashflowCapacityCalculator`, and `GoalStrategyEngine` verifying `I-GOAL-002`, `I-GOAL-003`, `I-GOAL-004`, `I-GOAL-005`.
- [x] `TASK-1.2` [GREEN]: Implement domain records (`FinancialGoal`, `CashflowProfile`, `GoalStrategy`, `GoalPriority`, `GoalStatus`, `GoalFeasibility`) and calculators in `br.com.wallet.goals.internal.engine`.
- [x] `TASK-1.3` [GREEN]: Implement multi-goal waterfall priority algorithm in `GoalStrategyEngine` verifying `I-GOAL-006` (`CRITICAL` $\succ$ `HIGH` $\succ$ `MEDIUM` $\succ$ `LOW`).
- [x] `TASK-1.4` [REFACTOR]: Ensure strict `HALF_EVEN` rounding and canonical monetary precision.

### Phase 2: Application Services & Use Cases
- [x] `TASK-2.1` [RED]: Write unit tests for `FinancialGoalService` covering lifecycle (create, update, pause, resume, cancel, markAchieved).
- [x] `TASK-2.2` [GREEN]: Implement `FinancialGoalUseCase` and `FinancialGoalService`.
- [x] `TASK-2.3` [GREEN]: Implement `GoalQueryUseCase` and `GoalQueryService`.
- [x] `TASK-2.4` [GREEN]: Implement `CashflowProfileUseCase` and `CashflowProfileService`.
- [x] `TASK-2.5` [GREEN]: Implement `GoalStrategyUseCase` and `GoalStrategyService` integrating with `BalanceUseCase.getBalance()`.

### Phase 3: Persistence & Database Schema
- [x] `TASK-3.1` [GREEN]: Add `goals` and `cashflow_profiles` tables with constraints and indexes to `../../docker/init/schema.sql`.
- [x] `TASK-3.2` [RED]: Write integration tests for `FinancialGoalDao` and `CashflowProfileDao` using Testcontainers PostgreSQL.
- [x] `TASK-3.3` [GREEN]: Implement `FinancialGoalDao` and `CashflowProfileDao` with Spring JDBC `JdbcTemplate` / `RowMapper`.

### Phase 4: REST API Exposure & Controller
- [x] `TASK-4.1` [RED]: Write MockMvc tests for `GoalController` verifying:
  - `POST /goals` (Create goal)
  - `PUT /goals/{id}` (Update goal)
  - `POST /goals/{id}/pause` & `POST /goals/{id}/resume`
  - `PUT /goals/cashflow` & `GET /goals/cashflow/wallet/{walletId}`
  - `GET /goals/{id}/strategy` (Live calculation)
  - `GET /goals/wallet/{walletId}/strategy-report` (Multi-goal waterfall report)
  - `POST /goals/simulate` (Stateless simulation)
- [x] `TASK-4.2` [GREEN]: Implement `GoalApi` interface and `GoalController` in `br.com.wallet.infrastructure.rest`.

### Phase 5: Architecture Verification & Convergence
- [x] `TASK-5.1` [GREEN]: Add `package-info.java` for `br.com.wallet.goals` and verify `ModulithArchitectureTest.verifyArchitecture()` with 0 violations.
- [x] `TASK-5.2` [GREEN]: Run full test suite (`./gradlew test jacocoTestReport`) and verify coverage $\ge 90\%$ for `goals`.
- [x] `TASK-5.3` [GREEN]: Author `../summaries/SUMMARY-002-financial-goal-engine.md`.
