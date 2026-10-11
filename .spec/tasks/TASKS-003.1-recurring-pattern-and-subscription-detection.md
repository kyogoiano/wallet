# 📝 Task Breakdown: TASKS-003.1 — Recurring Pattern & Subscription Detection

- **Associated Spec**: [`../SPEC-003.1-recurring-pattern-and-subscription-detection.md`](file:///.spec/SPEC-003.1-recurring-pattern-and-subscription-detection.md)
- **Associated Plan**: [`../plans/PLAN-003.1-recurring-pattern-and-subscription-detection.md`](file:///.spec/plans/PLAN-003.1-recurring-pattern-and-subscription-detection.md)
- **Status**: 📝 **Approved / Ready for Execution**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until all `[MUST]` criteria are green (`I-SDD-004`).
- **Atomic Spec Slicing Scope**: Max 250 lines (`I-SDD-006`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test / Artifact | Task IDs |
| :--- | :---: | :--- | :--- |
| `REQ-SUB-001` (In-Process User Outflows) | `[MUST]` | `SpendingEventListenerTest`, `SubscriptionDetectionIT` | `TASK-3.1.9`, `TASK-3.1.10` |
| `REQ-SUB-002` (Series Clustering $(T, W, C)$) | `[MUST]` | `SpendingEventListenerTest`, `SubscriptionDaoTest` | `TASK-3.1.3`, `TASK-3.1.9` |
| `REQ-SUB-003` (Lifecycle States & Activation Gate)| `[MUST]` | `RecurrencePatternEngineTest` ($N=1,2,3...$) | `TASK-3.1.6`, `TASK-3.1.8` |
| `REQ-SUB-004` (Cadence Math & $\text{nextExpectedAt}$)| `[MUST]` | `RecurrencePatternEngineTest` ($\overline{\Delta t}$ in days) | `TASK-3.1.4`, `TASK-3.1.8` |
| `REQ-SUB-005` (Population StdDev & $CV_A$ Variance)| `[MUST]` | `RecurrencePatternEngineTest` ($\sigma_A, CV_A$, `FIXED`/`VAR`) | `TASK-3.1.5`, `TASK-3.1.8` |
| `REQ-SUB-006` (Price Spike Detection & Alert) | `[MUST]` | `RecurrencePatternEngineTest`, `SubscriptionDetectionIT` | `TASK-3.1.7`, `TASK-3.1.13` |
| `REQ-SUB-007` (Schema DDL & Indexes) | `[MUST]` | `docker/init/schema.sql`, `SubscriptionDao` | `TASK-3.1.2`, `TASK-3.1.3` |
| `REQ-SUB-008` (REST Query Ingress) | `[MUST]` | `SubscriptionControllerTest`, `SubscriptionDetectionIT` | `TASK-3.1.11`, `TASK-3.1.12` |
| `REQ-SUB-009` (Cancellation Inference & Reactivation)| `[SHOULD]`| `RecurrencePatternEngineTest`, `SubscriptionDetectionIT` | `TASK-3.1.14` |
| `REQ-SUB-010` / `011` (Decision Seam) | `[SHOULD]` | `SubscriptionDecisionSeamTest` | `TASK-3.1.15` |
| `I-SUB-001` (Cadence Intervals) | `[MUST]` | `RecurrencePatternEngineTest` | `TASK-3.1.4` |
| `I-SUB-002` (Confidence Score & Gates) | `[MUST]` | `RecurrencePatternEngineTest` | `TASK-3.1.6` |
| `I-SUB-003` (Historical Baseline Price Spike)| `[MUST]` | `RecurrencePatternEngineTest`, `SubscriptionDetectionIT` | `TASK-3.1.7`, `TASK-3.1.13` |
| `I-SUB-004` (Cancellation & Reactivation) | `[SHOULD]` | `RecurrencePatternEngineTest` | `TASK-3.1.14` |
| `I-SUB-005` / `I-SUB-006` (Degradation & Decoupled) | `[SHOULD]` | `SubscriptionDecisionSeamTest` | `TASK-3.1.15` |
| `I-SUB-007` (Precision Separation) | `[MUST]` | `RecurrencePatternEngineTest` | `TASK-3.1.5` |
| `I-SUB-008` (Durable Idempotent Ingestion) | `[MUST]` | `SpendingEventListenerTest`, `SubscriptionDaoTest` | `TASK-3.1.2`, `TASK-3.1.9` |
| `I-SUB-010` (Strict Tenant Partitioning) | `[MUST]` | `SubscriptionDaoTest`, `SubscriptionControllerTest` | `TASK-3.1.3`, `TASK-3.1.11` |
| `I-SUB-011` (Outflow Observation Contract) | `[MUST]` | `SpendingEventListenerTest` | `TASK-3.1.9`, `TASK-3.1.10` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> Isolate context strictly to the active task card. Never pull unreferenced classes into working context.

```markdown
### 🎯 Active Task Card: TASK-3.1.X
- **Target Invariant**: I-SUB-00X, I-SUB-010, I-SUB-011
- **Target Requirement**: REQ-SUB-00X [MUST]
- **Target Files**: <TargetClass>.java, <TargetClassTest>.java
- **In-Scope Contracts**: Inputs -> Series History, Outputs -> Subscription Projection
- **Forbidden Boundary**: Zero direct mutations on ledger or accounts; no concrete AI evaluators.
```

---

## 3. Implementation Tasks (TDD Order: Red $\to$ Green $\to$ Refactor)

### Phase 1: Domain Contracts & Schema DDL ([MUST])
- [x] `TASK-3.1.1` [GREEN]: Create API models strictly within target subpackages (`api.dto.SubscriptionResponse`, `api.event.SubscriptionPriceSpikeEvent`, `api.model.Cadence`, `api.model.PriceState`, `api.model.SubscriptionStatus`, `api.model.VarianceType`) (`REQ-SUB-003`, `REQ-SUB-004`, `REQ-SUB-006`).
- [x] `TASK-3.1.2` [GREEN]: Update `docker/init/schema.sql`, `src/main/resources/schema.sql`, and `src/test/resources/schema.sql` with DDL for `subscriptions` table and durable idempotency table `intelligence_processed_events` (`event_id UUID PRIMARY KEY`, `tenant_id VARCHAR(64) NOT NULL`) (`REQ-SUB-007`, `I-SUB-008`, `I-SUB-010`, `I-SDD-007`).
- [x] `TASK-3.1.3` [GREEN]: Implement domain record `Subscription` and `SubscriptionDao` in `br.com.wallet.intelligence.internal.persistence` with PostgreSQL UPSERT, durable `eventId` tracking, and tenant-scoped queries, verified by `SubscriptionDaoTest` (`REQ-SUB-002`, `REQ-SUB-007`, `I-SUB-008`, `I-SUB-010`).

### Phase 2: Mathematical Recurrence Engine & Population Statistics ([MUST])
- [x] `TASK-3.1.4` [GREEN]: Implement unit tests in `RecurrencePatternEngineTest` for cadence intervals ($\overline{\Delta t}$: WEEKLY, BI_WEEKLY, MONTHLY, ANNUAL, IRREGULAR) and projected $\text{nextExpectedAt} = t_{\text{last}} + \text{round}(\overline{\Delta t})$ days (`null` for IRREGULAR) (`REQ-SUB-004`, `I-SUB-001`).
- [x] `TASK-3.1.5` [GREEN]: Implement unit tests in `RecurrencePatternEngineTest` for population statistics ($\mu_A$, population $\sigma_A = \sqrt{\frac{1}{N}\sum (A_i - \mu_A)^2}$, $CV_A = \sigma_A / \mu_A$) with scale 6 intermediate precision and amount variance classification (`FIXED` vs `VARIABLE` at $CV_A \le 0.05$) (`REQ-SUB-005`, `I-SUB-002`, `I-SUB-007`).
- [x] `TASK-3.1.6` [GREEN]: Implement unit tests in `RecurrencePatternEngineTest` for Confidence formula ($\text{Confidence} = \min(1.00, \frac{N}{3}(1.00 - \min(0.50, CV_A)))$) and exact mathematical boundary gates:
  - $N=3, CV_A=0 \implies \text{Confidence}=1.00 \implies \text{ACTIVE}$
  - $N=3, CV_A=0.30 \implies \text{Confidence}=0.70 \implies \text{ACTIVE}$
  - $N=3, CV_A > 0.30 \implies \text{Confidence} < 0.70 \implies \text{CANDIDATE}$
  - $N=1 \implies \text{DISCOVERED}, N=2 \implies \text{CANDIDATE}$ (`REQ-SUB-003`, `I-SUB-002`).
- [x] `TASK-3.1.7` [GREEN]: Implement unit tests in `RecurrencePatternEngineTest` for pre-observation baseline price spike detection: $A_{\text{new}} \ge \mu_A(t) \times 1.05$ sets `priceState = PRICE_SPIKE_DETECTED` and creates `SubscriptionPriceSpikeEvent`, while status remains `ACTIVE` (`REQ-SUB-006`, `I-SUB-003`).
- [x] `TASK-3.1.8` [GREEN]: Implement `RecurrencePatternEngine` in `br.com.wallet.intelligence.internal.engine` passing all mathematical unit tests with Zero Vibe Coding (`I-TDD-002`).

### Phase 3: Outflow Ingestion, Filtering & Series Clustering ([MUST])
- [x] `TASK-3.1.9` [GREEN]: Implement unit tests in `SpendingEventListenerTest` verifying that:
  - Only `TransferCompletedEvent` with `origin == OperationOrigin.USER` is clustered by $(T, W, C)$ with `walletId = from`, `counterpartyId = to` (`I-SUB-011`, `REQ-SUB-001`, `REQ-SUB-002`).
  - Events with non-USER origin are discarded (`I-SUB-011`).
  - `WithdrawCompletedEvent` enters clustering only if a canonical external counterparty identity is present; events without a stable counterparty MUST NOT enter subscription series (`I-SUB-011`).
  - $N$ strictly equals `observed_cycles` incrementing per qualifying observation ($N = 1, 2, 3...$).
  - Durable `eventId` deduplication via `SubscriptionDao` prevents duplicate cycle counting across restarts (`I-SUB-008`).
- [x] `TASK-3.1.10` [GREEN]: Update `SpendingEventListener` in `br.com.wallet.intelligence.internal.listener` as pure ingress adapter: qualifies outflows, verifies durable `eventId` idempotency, delegates to `RecurrencePatternEngine`, and persists projection via `SubscriptionDao` (`REQ-SUB-001`, `REQ-SUB-002`).

### Phase 4: REST Query Ingress & Tenant Scoping ([MUST])
- [x] `TASK-3.1.11` [GREEN]: Implement unit/mock tests in `SubscriptionControllerTest` for `GET /api/v1/intelligence/subscriptions/{walletId}?status={status}`, verifying authenticated tenant context resolution and filtering (`REQ-SUB-008`, `I-SUB-010`).
- [x] `TASK-3.1.12` [GREEN]: Implement `SubscriptionController` in `br.com.wallet.intelligence.internal.rest`.

### Phase 5: Integration, Decision Seam & Verification ([MUST] / [SHOULD])
- [x] `TASK-3.1.13` [GREEN]: Write end-to-end integration test `SubscriptionDetectionIT` with Testcontainers verifying:
  - 3 monthly transfers produce `ACTIVE`, `MONTHLY`, $\text{Confidence} \ge 0.70$ in PostgreSQL (`REQ-SUB-003`).
  - 4th transfer with +15% price spike sets `priceState = PRICE_SPIKE_DETECTED` and publishes semantically verified `SubscriptionPriceSpikeEvent` (asserting `tenantId`, `walletId`, `counterpartyId`, baseline $\mu_A(t)$, new amount $A_{\text{new}}$) while status remains `ACTIVE` (`REQ-SUB-006`, `I-SUB-003`).
  - Cross-tenant isolation: Tenant B transactions do not alter Tenant A subscription series (`I-SUB-010`).
- [x] `TASK-3.1.14` [GREEN]: Implement cancellation inference & reactivation test: elapsed $> 1.50 \times \overline{\Delta t} \implies \text{CANCELLED_INFERRED}$; subsequent transaction reactivates to `CANDIDATE` recalculating over the full historical series (`REQ-SUB-009`, `I-SUB-004`).
- [x] `TASK-3.1.15` [GREEN]: Implement integration seam test `SubscriptionDecisionSeamTest` verifying `DecisionQuestion<SubscriptionClassification>` exposure and `DecisionUnavailable` failure isolation (`REQ-SUB-010`, `REQ-SUB-011`, `I-SUB-005`).
- [x] `TASK-3.1.16` [GREEN]: **Final Convergence Gate**: Run `ModulithArchitectureTest.verifyArchitecture()` and full test suite (`./gradlew test`). Execute bi-directional equivalence audit (`I-SDD-003`) and author `SUMMARY-003.1-recurring-pattern-and-subscription-detection.md` with Practical Verification Guide (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [x] All unit tests pass: `./gradlew test`
- [x] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [x] `subscriptions` table migration verified in `schema.sql` with `UNIQUE (tenant_id, wallet_id, counterparty_id)`
- [x] Durable `eventId` deduplication table `intelligence_processed_events` verified in PostgreSQL
- [x] Mathematical boundary triads pass with canonical `BigDecimal` and population standard deviation
- [x] Price spike detection proven against pre-observation historical baseline with semantically verified event
- [x] Strict multi-tenant isolation proven with zero series cross-contamination
- [x] Zero mutation against ledger or accounts persistence verified
