# 📝 Task Breakdown: TASKS-003.2 — Forward Cashflow Forecasting & Goals Integration

- **Associated Spec**: [`../SPEC-003.2-cashflow-forecasting-and-goals-integration.md`](file:///.spec/SPEC-003.2-cashflow-forecasting-and-goals-integration.md)
- **Associated Plan**: [`../plans/PLAN-003.2-cashflow-forecasting-and-goals-integration.md`](file:///.spec/plans/PLAN-003.2-cashflow-forecasting-and-goals-integration.md)
- **Status**: 📝 **Approved / Ready for Execution**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until all `[MUST]` criteria are green (`I-SDD-004`).
- **Atomic Spec Slicing Scope**: Max 250 lines (`I-SDD-006`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test / Artifact | Task IDs |
| :--- | :---: | :--- | :--- |
| `REQ-CASH-001` (Forward Liabilities $L_7, L_{14}, L_{30}$) | `[MUST]` | `CashflowForecastingEngineTest`, `CashflowForecastIT` | `TASK-3.2.3`, `TASK-3.2.6` |
| `REQ-CASH-002` (Current-Balance Coverage Shortfall) | `[MUST]` | `CashflowForecastingEngineTest`, `CashflowForecastIT` | `TASK-3.2.4`, `TASK-3.2.6` |
| `REQ-CASH-003` (Committed Expenses & Installments) | `[MUST]` | `CashflowForecastingEngineTest`, `CashflowForecastIT` | `TASK-3.2.5`, `TASK-3.2.6` |
| `REQ-CASH-004` (Projections REST Ingress) | `[MUST]` | `CashflowControllerTest`, `CashflowForecastIT` | `TASK-3.2.9`, `TASK-3.2.10` |
| `REQ-CASH-005` (Goals Profile Idempotent Sync) | `[MUST]` | `CashflowForecastingServiceTest`, `CashflowForecastIT` | `TASK-3.2.7`, `TASK-3.2.8` |
| `REQ-CASH-006` (Shortfall Transition Alert Event) | `[SHOULD]` | `CashflowForecastingServiceTest` | `TASK-3.2.12` |
| `I-CASH-001` (Projected Occurrence Expansion $O_H$) | `[MUST]` | `CashflowForecastingEngineTest` | `TASK-3.2.3` |
| `I-CASH-002` (Current-Balance Coverage Shortfall) | `[MUST]` | `CashflowForecastingEngineTest` | `TASK-3.2.4` |
| `I-CASH-003` (Installment Exclusion & Anti-Double-Count)| `[MUST]` | `CashflowForecastingEngineTest` | `TASK-3.2.5` |
| `I-CASH-004` (Goals Ownership & Buffer Preservation) | `[MUST]` | `CashflowForecastingServiceTest`, `CashflowForecastIT` | `TASK-3.2.7`, `TASK-3.2.8` |
| `I-CASH-005` (Server Evaluation Timestamp $t_0$) | `[MUST]` | `CashflowForecastingEngineTest` (fixed clock) | `TASK-3.2.3` |
| `I-CASH-006` (Non-Locking Projection Read) | `[MUST]` | `CashflowForecastingServiceTest` | `TASK-3.2.7` |
| `I-CASH-007` (Strict Authenticated Tenant Partitioning)| `[MUST]` | `CashflowControllerTest`, `CashflowForecastIT` | `TASK-3.2.9`, `TASK-3.2.11` |
| `I-CASH-008` (Ephemeral Deterministic Architecture) | `[MUST]` | Schema check (zero migrations) | `TASK-3.2.2` |

---

## 2. Active Task Card Protocol (Context Hygiene)

```markdown
### 🎯 Active Task Card: TASK-3.2.X
- **Target Invariant**: I-CASH-00X, I-CASH-007
- **Target Requirement**: REQ-CASH-00X [MUST]
- **Target Files**: <TargetClass>.java, <TargetClassTest>.java
- **In-Scope Contracts**: Inputs -> Subscriptions & Balance, Outputs -> Projections & Profile Sync
- **Forbidden Boundary**: Zero direct mutations on ledger; zero direct database access to goals tables.
```

---

## 3. Implementation Tasks (TDD Order: Red $\to$ Green $\to$ Refactor)

### Phase 1: API Models & Ephemeral Architecture Gate ([MUST])
- [x] `TASK-3.2.1` [GREEN]: Create API models in `br.com.wallet.intelligence.api` (`model.CashflowStatus`, `dto.CashflowProjectionResponse`, `dto.CashflowSyncResponse`, `event.CashflowShortfallAlertEvent`) (`REQ-CASH-001`, `REQ-CASH-004`, `REQ-CASH-005`).
- [x] `TASK-3.2.2` [GREEN]: Assert ephemeral calculation model: zero `cashflow_forecasts` tables or migrations introduced in `schema.sql`; forecast state is calculated on-demand and not persisted (`I-CASH-008`).

### Phase 2: Mathematical Forecasting Engine & Occurrence Expansion ([MUST])
- [x] `TASK-3.2.3` [GREEN]: Implement unit tests in `CashflowForecastingEngineTest` for recurrence occurrence expansion $O_H(S, t_0)$:
  - Evaluation uses injected `Clock` instant as $t_0$; same inputs produce deterministic identical projections (`I-CASH-005`).
  - Canonical cadence matrix ($t_0 \le t \le t_0 + H$): WEEKLY (7d $\to 1$, 14d $\to 2$, 30d $\to 4 \implies L_{30} = 200.00$), BI_WEEKLY (14d $\to 1$, 30d $\to 2$), MONTHLY (30d $\to 1$), IRREGULAR ($O_H = \emptyset \implies L_H = 0.00$) (`I-CASH-001`, `REQ-CASH-001`).
- [x] `TASK-3.2.4` [GREEN]: Implement unit tests in `CashflowForecastingEngineTest` for current-balance coverage shortfall:
  - $\text{Shortfall}_H = \max(0.00, L_H - \text{Balance})$; status `DEFICIT_WARNING` when $> 0$, `SURPLUS` when $0$ (`I-CASH-002`, `REQ-CASH-002`).
- [x] `TASK-3.2.5` [GREEN]: Implement unit tests in `CashflowForecastingEngineTest` for monthly committed normalization:
  - `INSTALLMENT` excluded from perpetual `monthlyCommittedExpenses`, but included in discrete forward liability $L_H$ while active (`I-CASH-003`, `REQ-CASH-003`).
  - Monthly committed and forward liabilities exposed as separate dimensions; `Shortfall_H` calculated exclusively from $L_H$ and current balance; `monthlyCommittedExpenses` MUST NOT be added to $L_H$ (`I-CASH-003`).
- [x] `TASK-3.2.6` [GREEN]: Implement pure domain `CashflowForecastingEngine` in `br.com.wallet.intelligence.internal.engine` (zero Spring/DB/REST/Goals dependencies), passing all mathematical unit tests with Zero Vibe Coding (`I-TDD-002`).

### Phase 3: Service Orchestration & Goals Integration ([MUST])
- [x] `TASK-3.2.7` [GREEN]: Implement unit tests in `CashflowForecastingServiceTest` with mocks verifying:
  - Query active subscriptions via `SubscriptionDao.findByWalletId(tenantId, walletId, ACTIVE)`.
  - Non-locking balance read via `BalanceUseCase.getBalance(walletId)` (`I-CASH-006`).
  - Resolve user identity required by existing `goals::api` `SaveCashflowProfileCommand` contract using existing public API. No new identity-resolution API, repository, or direct Goals persistence may be introduced (`I-CASH-004`).
  - Query existing profile via existing `goals::api` contract and preserve `minimumSafetyBuffer` and `monthlyIncome`.
  - Save profile via existing `goals::api` contract idempotently (`REQ-CASH-005`).
- [x] `TASK-3.2.8` [GREEN]: Implement production `CashflowForecastingService` in `br.com.wallet.intelligence.internal.service`.

### Phase 4: Authenticated REST Ingress & Tenant Scoping ([MUST])
- [x] `TASK-3.2.9` [GREEN]: Implement unit tests in `CashflowControllerTest` verifying:
  - `GET /api/v1/intelligence/cashflow/{walletId}/projections` resolves tenant from authenticated security context (`REQ-CASH-004`, `I-CASH-007`).
  - `POST /api/v1/intelligence/cashflow/{walletId}/sync-goals` triggers idempotent sync and returns 200 (`REQ-CASH-005`).
- [x] `TASK-3.2.10` [GREEN]: Implement `CashflowController` in `br.com.wallet.intelligence.internal.rest`.

### Phase 5: Integration, Seams & Final Convergence ([MUST] / [SHOULD])
- [x] `TASK-3.2.11` [GREEN]: Write end-to-end integration test `CashflowForecastIT` with Testcontainers verifying:
  - Weekly subscriptions producing multiple occurrences in 30d liability.
  - Shortfall detection against real account balance via non-locking `BalanceUseCase`.
  - Verification that forecasting path does not issue `SELECT FOR UPDATE` or mutate ledger/account state (`I-CASH-006`, `I-INTEL-001`).
  - Installment filtering and goals profile sync.
  - Cross-tenant isolation: Tenant B transactions do not bleed into Tenant A forecast (`I-CASH-007`).
- [x] `TASK-3.2.12` [GREEN]: Implement transition alert test verifying `CashflowShortfallAlertEvent` emission when transitioning `SURPLUS` $\to$ `DEFICIT_WARNING` during sync (`REQ-CASH-006`).
- [x] `TASK-3.2.13` [GREEN]: **Final Convergence Gate**: Run `ModulithArchitectureTest.verifyArchitecture()` and full test suite (`./gradlew test`). Execute bi-directional equivalence audit (`I-SDD-003`) and author `SUMMARY-003.2-cashflow-forecasting-and-goals-integration.md` with Practical Verification Guide (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [x] All unit tests pass: `./gradlew test`
- [x] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [x] Multiple occurrence expansion $O_H$ verified for weekly/bi-weekly subscriptions
- [x] Installment exclusion from `monthlyCommittedExpenses` verified
- [x] Goals profile buffer and income preservation verified
- [x] Strict multi-tenant isolation proven with zero cross-tenant contamination
- [x] Zero database mutations to ledger or accounts tables verified
