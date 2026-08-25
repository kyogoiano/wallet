# 📝 Task Breakdown: TASKS-001 — Smart Savings Automation (br.com.wallet.savings)

- **Associated Spec**: [`SPEC-001-smart-savings-automation.md`](file:///.spec/SPEC-001-smart-savings-automation.md)
- **Associated Plan**: [`PLAN-001-smart-savings-automation.md`](file:///.spec/PLAN-001-smart-savings-automation.md)
- **Status**: Completed / Verified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-23

---

## 1. Traceability Matrix

| Requirement / Invariant ID | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-SAV-001` | `SavingsPlanServiceTest`, `SavingsPlanDaoTest` | `TASK-1.6`, `TASK-1.9` |
| `REQ-SAV-002` | `SavingsEventListenerTest` | `TASK-1.8` |
| `REQ-SAV-002A` | `SavingsEventListenerTest` (`origin != USER` guard) | `TASK-1.1`, `TASK-1.8` |
| `REQ-SAV-003` | `RoundUpCalculatorTest` | `TASK-1.2` |
| `REQ-SAV-003A` | `RoundUpCalculatorTest` (step > 0, exact multiple = 0) | `TASK-1.2` |
| `REQ-SAV-003B` | `ThresholdCalculatorTest` | `TASK-1.4` |
| `REQ-SAV-004` | `PercentageCalculatorTest` | `TASK-1.3` |
| `REQ-SAV-004A` | `PercentageCalculatorTest` (`HALF_EVEN` scale=2) | `TASK-1.3` |
| `REQ-SAV-005` | `SavingsRuleEngineTest` (Percentage $\rightarrow$ Projected Balance $\rightarrow$ Threshold) | `TASK-1.5` |
| `REQ-SAV-006` | `SavingsExecutionServiceTest` | `TASK-1.7` |
| `REQ-SAV-007` | `SavingsQueryServiceTest`, `SavingsExecutionHistoryDaoTest` | `TASK-1.6`, `TASK-1.10` |
| `REQ-SAV-009` | `SavingsRuleEngineTest` (Event-Specific Evaluation Order) | `TASK-1.5` |
| `I-SAVINGS-001` | `SavingsExecutionServiceTest`, `RoundUpMicroSavingsIT` (Loop Guard) | `TASK-1.1`, `TASK-1.7`, `TASK-1.11` |
| `I-SAVINGS-002` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-1.0`, `TASK-1.12` |
| `I-SAVINGS-003` | `SavingsEventListenerTest` (Fault & Transaction Isolation) | `TASK-1.8`, `TASK-1.11` |
| `I-SAVINGS-004` | `SavingsRuleEngineTest` (Liquidity Intent Clamping) | `TASK-1.5`, `TASK-1.7` |
| `I-SAVINGS-005` | `SavingsDepositSweepIT` (Eventual Capability Consistency) | `TASK-1.11` |
| `I-MODULITH-001` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-1.0`, `TASK-1.12` |
| `I-MODULITH-002` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-1.0`, `TASK-1.12` |

---

## 2. Implementation Tasks (Refined Phased TDD Order)

```text
                 PHASE 0: ARCHITECTURAL SKELETON
                  (TASK-1.0: Modulith Boundary)
                               │
                               ▼
               PHASE 1: LEDGER CONTRACT EXTENSION
                 (TASK-1.1: OperationOrigin Flow)
                               │
                               ▼
                  PHASE 2: PURE DOMAIN LOGIC
              (TASK-1.2: RoundUp, TASK-1.3: Percentage,
               TASK-1.4: Threshold, TASK-1.5: RuleEngine)
                               │
                               ▼
                     PHASE 3: PERSISTENCE
               (TASK-1.6: Schema DDL & JDBC DAOs)
                               │
                               ▼
                      PHASE 4: EXECUTION
             (TASK-1.7: SavingsExecutionService & Status)
                               │
                               ▼
                  PHASE 5: EVENT INTEGRATION
            (TASK-1.8: SavingsEventListener & Loop Guard)
                               │
                               ▼
                     PHASE 6: PUBLIC API
           (TASK-1.9: SavingsPlanService, TASK-1.10: Query)
                               │
                               ▼
               PHASE 7: INTEGRATION & CONVERGENCE
             (TASK-1.11: Scenario ITs, TASK-1.12: Report)
```

---

### Phase 0: Architectural Skeleton & Modulith Boundary
- [x] `TASK-1.0` Create `br.com.wallet.savings` module skeleton:
  - Create `br.com.wallet.savings.package-info.java` with `@ApplicationModule(displayName = "Smart Savings Automation", allowedDependencies = {"ledger::api", "core::api", "core"})`.
  - Tag `savings.api` with `@NamedInterface("api")`.
  - Create stub interfaces in `savings.api` (`SavingsPlanUseCase`, `SavingsQueryUseCase`).
  - Run `./gradlew test --tests ModulithArchitectureTest` to verify the boundary passes immediately.

### Phase 1: Ledger Contract Extension & Origin Propagation
- [x] `TASK-1.1` Introduce `OperationOrigin` and propagate through Ledger:
  - Create `OperationOrigin` enum (`USER`, `SAVINGS_AUTOMATION`, `SYSTEM`, `REVERSAL`) in `core::api` / `core.context`.
  - Enrich `DepositCompletedEvent` and `TransferCompletedEvent` records with `OperationOrigin origin`.
  - Enrich `Transfer` context and `Deposit` context in `ledger.api.context`.
  - Implement `TransferOriginPropagationTest` to verify end-to-end propagation from command context to published event.

### Phase 2: Pure Deterministic Domain & Event-Specific Rule Engine (Unit Tests First)
- [x] `TASK-1.2` Implement `RoundUpCalculator` & `RoundUpCalculatorTest`:
  - Formula: $\lceil \text{amount} / \text{step} \rceil \times \text{step} - \text{amount}$.
  - Strictly positive step check ($\text{step} > 0$). Returns `0.00` if exact multiple.
- [x] `TASK-1.3` Implement `PercentageCalculator` & `PercentageCalculatorTest`:
  - Formula: $(\text{amount} \times \text{percentage} / 100).\text{setScale}(2, \text{RoundingMode.HALF\_EVEN})$.
  - Validates percentage $> 0$ and $\le 100$.
- [x] `TASK-1.4` Implement `ThresholdCalculator` & `ThresholdCalculatorTest`:
  - Formula: $\max(0, \text{currentBalance} - \text{ceilingThreshold})$.
- [x] `TASK-1.5` Implement `SavingsRuleEngine` & `SavingsRuleEngineTest`:
  - **Deposit Flow**: Percentage Sweep $\rightarrow$ Projected Balance ($\text{projectedBalance} = \text{currentBalance} - \text{percentageSweep}$) $\rightarrow$ Threshold Sweep.
  - **Transfer Flow**: Round-Up only.
  - Liquidity clamping: $\text{maxSweep} = \max(0, \text{currentBalance} - \text{minRetainedBalance})$.
  - Returns a list of intended sweep actions (each rule produces its own distinct sweep action with its own `ruleId`).

### Phase 3: Persistence & Deduplication Schema
- [x] `TASK-1.6` Database Migration & JDBC DAOs:
  - Add tables `savings_plans`, `savings_rules`, `savings_execution_history` with `UNIQUE(operation_id)` in `docker/init/schema.sql` (and test schema).
  - Implement `SavingsPlanDao`, `SavingsRuleDao`, `SavingsExecutionHistoryDao`.
  - Author DAO unit/slice tests.

### Phase 4: Execution Service
- [x] `TASK-1.7` Implement `SavingsExecutionService` & `SavingsExecutionServiceTest`:
  - Generates deterministic `operationId = SHA256(sourceOperationId + ruleId + "SAVINGS_SWEEP")`.
  - Checks Layer 1 deduplication against `savings_execution_history`.
  - Invokes `ledger.api.TransferFundsUseCase` with `origin = SAVINGS_AUTOMATION`.
  - Maps results/exceptions to `SavingsExecutionStatus` (`EXECUTED`, `SKIPPED_INSUFFICIENT_FUNDS`, `REJECTED_BY_FRAUD`, `FAILED_RETRYABLE`, `FAILED_PERMANENT`).

### Phase 5: Event Integration & Asynchronous Listener
- [x] `TASK-1.8` Implement `SavingsEventListener` (`@ApplicationModuleListener`):
  - `onDeposit(DepositCompletedEvent)`: checks `origin == USER`, queries active plans, calls engine deposit flow, executes sweeps.
  - `onTransfer(TransferCompletedEvent)`: checks `origin == USER`, queries active plans, calls engine transfer flow (round-up), executes sweeps.
  - $O(1)$ Loop guard: immediately returns if `origin == SAVINGS_AUTOMATION`.

### Phase 6: Public API Services
- [x] `TASK-1.9` Implement `SavingsPlanService`:
  - Implements `SavingsPlanUseCase`: `createPlan`, `pausePlan`, `resumePlan`, `deletePlan`, `getPlansForWallet`.
- [x] `TASK-1.10` Implement `SavingsQueryService`:
  - Implements `SavingsQueryUseCase`: `getMetrics(walletId)` and `getExecutionHistory(walletId)`.

### Phase 7: Integration Scenarios, Coverage & Convergence
- [x] `TASK-1.11` Implement Integration Scenarios:
  - `SavingsDepositSweepIT`: Full deposit flow with automated percentage and threshold sweeps to target savings wallet.
  - `RoundUpMicroSavingsIT`: Outgoing transfer flow with round-up sweep, asserting zero recursive loops.
- [x] `TASK-1.12` Verification, Coverage & Execution Summary:
  - Run full test suite with JaCoCo (`./gradlew test jacocoTestReport`).
  - Verify line coverage meets $\ge 85\%$ for `savings` domain and rule engine.
  - Author `.spec/summaries/SUMMARY-001-smart-savings-automation.md`.

---

## 3. Convergence & Verification Checklist

- [x] All 13 tasks (`TASK-1.0` through `TASK-1.12`) completed cleanly.
- [x] `ModulithArchitectureTest.verifyArchitecture()` passes with zero boundary violations.
- [x] `./gradlew test` passes 100% with zero regressions.
- [x] JaCoCo coverage $\ge 85\%$ on `br.com.wallet.savings`.
- [x] Summary written to `.spec/summaries/SUMMARY-001-smart-savings-automation.md`.
