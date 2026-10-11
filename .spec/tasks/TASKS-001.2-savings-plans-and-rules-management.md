# 📋 Implementation Tasks: TASKS-001.2 — Savings Plans and Dynamic Rules Management

- **Specification Reference**: [`../SPEC-001.2-savings-plans-and-rules-management.md`](file:///.spec/SPEC-001.2-savings-plans-and-rules-management.md)
- **Architecture Plan**: [`../plans/PLAN-001.2-savings-plans-and-rules-management.md`](file:///.spec/plans/PLAN-001.2-savings-plans-and-rules-management.md)
- **Status**: Completed / Verified

---

## 1. Traceability Matrix

| Requirement ID | Test Identifier | Implementation Task |
| :--- | :--- | :--- |
| `REQ-SAV-010` | `SavingsPlanServiceTest`, `SavingsControllerTest`, `SavingsRestIT` | `TASK-1.1`, `TASK-1.2`, `TASK-1.4` |
| `REQ-SAV-011` | `SavingsPlanServiceTest`, `SavingsControllerTest`, `SavingsRestIT` | `TASK-1.1`, `TASK-1.2`, `TASK-1.4` |
| `REQ-SAV-012` | `SavingsPlanPersistenceIT`, `SavingsPlanServiceTest`, `SavingsControllerTest`, `SavingsRestIT` | `TASK-1.1`, `TASK-1.2`, `TASK-1.4`, `TASK-1.6` |
| `REQ-SAV-013` | `SavingsControllerTest`, `SavingsRestIT` | `TASK-1.3`, `TASK-1.4`, `TASK-1.6` |
| `REQ-SAV-014` | `SavingsControllerTest`, `SavingsRestIT` | `TASK-1.3`, `TASK-1.4` |
| `I-SAV-RULE-001` | `SavingsPlanServiceTest` | `TASK-1.2` |
| `I-MODULITH-001` | `ModulithArchitectureTest` | `TASK-1.5` |
| `I-MODULITH-002` | `ModulithArchitectureTest` | `TASK-1.5` |

---

## 2. Task Sequence & TDD Workflow

### Phase 1: DAO and Use Case Extensions
- [x] `TASK-1.1`: Extend `SavingsRuleDao` (`findById`, `updateActive`, `deleteById`) and `SavingsPlanDao` (`findAll`, `findBySourceWalletId`, `findByTargetWalletId`, `findByWalletId`, `findActiveByTargetWalletId`).
- [x] `TASK-1.2`: Update `SavingsPlanUseCase` interface and `SavingsPlanService` (`listPlans`, `getPlansBySourceWallet`, `getPlansByTargetWallet`, `getPlansForWallet`, `getPlan`, `addRule`, `removeRule`, `toggleRule`, `getRulesForPlan`) with unit tests (`SavingsPlanServiceTest`).

### Phase 2: REST Layer & Error Handling
- [x] `TASK-1.3`: Add `NOT_FOUND` to `ErrorCode` and configure `NoSuchElementException` handler in `ApiExceptionHandler`.
- [x] `TASK-1.4`: Define `SavingsApi` interface and implement `SavingsController` under `/savings` with explicit `/plans` (paginated list), `/plans/source/{sourceWalletId}`, `/plans/target/{targetWalletId}`, and `/plans/wallet/{walletId}` endpoints, verified with unit tests (`SavingsControllerTest`).

### Phase 3: Verification, Database Integration Tests & Convergence
- [x] `TASK-1.5`: Verify Spring Modulith architectural boundaries (`ModulithArchitectureTest`).
- [x] `TASK-1.6`: Implement comprehensive PostgreSQL integration tests: `SavingsPlanPersistenceIT` (DAO verification) and `SavingsRestIT` (end-to-end REST API verification).
