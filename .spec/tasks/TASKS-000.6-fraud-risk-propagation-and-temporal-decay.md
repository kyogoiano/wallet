# 📝 Task Breakdown: TASKS-000.6 — Fraud Risk Propagation, Temporal Decay & Hardened Hand-Rolled Job Engine

- **Associated Spec**: [`../SPEC-000.6-fraud-risk-propagation-and-temporal-decay.md`](file:///.spec/SPEC-000.6-fraud-risk-propagation-and-temporal-decay.md)
- **Associated Plan**: [`../plans/PLAN-000.6-fraud-risk-propagation-and-temporal-decay.md`](file:///.spec/plans/PLAN-000.6-fraud-risk-propagation-and-temporal-decay.md)
- **Status**: Not Started
- **Target Release**: Wallet Service V4.x — Phase 0.6

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-PROP-001` (Relationship Edge Weights) | `PathInfluenceCalculatorTest.shouldApplyCorrectEdgeWeights()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-PROP-002` (Temporal Decay with `as_of`) | `PathInfluenceCalculatorTest.shouldDecayExponentiallyRelativeToAsOf()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-PROP-003` (Multi-Hop Traversal & Bounds) | `PathInfluenceCalculatorTest.shouldComputeMultiHopProductWithBounds()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-PROP-004` (Multi-Path Probabilistic Union) | `MultiPathAggregatorTest.shouldAggregateProbabilisticUnionWithoutDoubleCounting()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-PROP-005` (Dimension Isolation & Versioning) | `PostgresRiskPropagationDaoIT.shouldPersistPropagatedRiskWithoutAlteringDirectRisk()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-PROP-006` (Token Leases & DB Uniqueness) | `PostgresPropagationJobDaoIT.shouldEnforceUniqueActiveJobsAndTokenLeases()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-PROP-007` (Transactional Outbox Alert) | `DefaultRiskPropagationEngineTest.shouldStageOutboxAlertWhenThresholdExceeded()` | `TASK-4.1`, `TASK-4.2` |
| `I-PROP-001` (Range $[0.0, 1.0]$) | `MultiPathAggregatorTest.shouldGuaranteeBoundsWithinZeroAndOne()` | `TASK-1.1`, `TASK-1.2` |
| `I-PROP-002` (Monotonic Decay) | `PathInfluenceCalculatorTest.shouldMonotonicallyApproachZeroAsTimeIncreases()` | `TASK-1.1`, `TASK-1.2` |
| `I-PROP-003` (Sub-Linear Saturation) | `MultiPathAggregatorTest.shouldSublinearlySaturateWithMultiplePaths()` | `TASK-1.1`, `TASK-1.2` |
| `I-PROP-004` (Asynchronous Guarantee) | `PropagationJobWorkerTest.shouldExecuteInThreadPoolWithoutBlockingCore()` | `TASK-3.1`, `TASK-3.2` |
| `I-PROP-005` (Risk Dimension Isolation) | `DefaultRiskPropagationEngineTest.shouldIsolateRiskDimensions()` | `TASK-4.1`, `TASK-4.2` |
| `I-PROP-006` (Bounded Traversal Invariant) | `PostgresRiskPropagationDaoIT.shouldRespectMaxHopsAndCycleBreakers()` | `TASK-2.1`, `TASK-2.2` |
| `I-PROP-007` (DB Active Job Uniqueness) | `PostgresPropagationJobDaoIT.shouldRejectDuplicateActiveJobInsertions()` | `TASK-3.1`, `TASK-3.2` |
| `I-PROP-008` (Worker Token Lease Safety) | `PropagationJobWorkerIT.shouldRejectCompletionWhenWorkerTokenMismatches()` | `TASK-3.1`, `TASK-3.2` |
| `I-PROP-009` (Transactional Alert Durability) | `DefaultRiskPropagationEngineTest.shouldStageEventInsideTransaction()` | `TASK-4.1`, `TASK-4.2` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Domain & Mathematical Models (`:fraud`)
- [x] `TASK-1.1` [RED]: Write unit tests `PathInfluenceCalculatorTest` (testing edge weight mappings, exponential half-life decay $\lambda = \frac{\ln 2}{t_{1/2}}$, deterministic elapsed time $\Delta t = \text{as\_of} - t_{\text{event}}$, and multi-hop product $I(p, t)$) and `MultiPathAggregatorTest` (testing probabilistic union $1 - \prod (1 - I)$, sub-linear saturation, and range $[0.0, 1.0]$).
- [x] `TASK-1.2` [GREEN]: Implement mathematical engine components in `br.com.wallet.fraud.intelligence.internal.propagation`:
  - `PropagationConfig` (configurable half-life, edge weights, traversal limits, alert thresholds)
  - `PathInfluenceCalculator` (calculates path influence with exponential decay relative to `as_of`)
  - `MultiPathAggregator` (probabilistic union aggregator)
  - Domain records: `PathInfluence`, `PropagatedEntityRisk`, `PropagationResult`, `EntityRiskPropagationDetectedEvent`.
- [x] `TASK-1.3` [REFACTOR]: Optimize math routines, avoid precision overflows, and ensure immutability and test coverage.

### Phase 2: Graph Path Traversal & Persistence (`:fraud`)
- [x] `TASK-2.1` [RED]: Write integration test `PostgresRiskPropagationDaoIT` using Testcontainers PostgreSQL verifying:
  - Recursive CTE path traversal discovering multi-hop paths with temporal timestamps from `fraud_relationship_events.occurred_at <= as_of`.
  - Bounded hop traversal (`max-hops <= 3`) and acyclic path checks.
  - Updating `propagated_risk`, `propagation_model_version`, and `propagation_evaluated_at` on `fraud_entities` with zero mutation to `direct_risk`.
- [x] `TASK-2.2` [GREEN]: Update `../../docker/init/schema.sql` (columns `propagation_model_version`, `propagation_evaluated_at`, `fraud_propagation_jobs` table, and partial unique index) and implement `PostgresRiskPropagationDao` with recursive path query and batch update operations.
- [x] `TASK-2.3` [REFACTOR]: Index optimization and query performance tuning.

### Phase 3: Hardened Job Queue & Worker Pool (`:fraud`)
- [x] `TASK-3.1` [RED]: Write integration tests `PostgresPropagationJobDaoIT`, `PropagationJobWorkerIT`, and `PropagationEvaluationDispatcherTest` verifying:
  - Job enqueuing with status `PENDING`, explicit `as_of`, and database-enforced unique constraint rejection on active duplicates (`I-PROP-007`).
  - Worker claim via `SELECT ... FOR UPDATE SKIP LOCKED` acquiring `worker_token` and `lease_until`.
  - Lease heartbeat renewal and safe completion matching `worker_token` (`I-PROP-008`).
  - Reaper recovery of expired leases (`RUNNING` with `lease_until < now()`).
- [x] `TASK-3.2` [GREEN]: Implement:
  - `PropagationJobRepository` / `PostgresPropagationJobDao`
  - `DefaultPropagationDispatcher` (implements `PropagationEvaluationDispatcher`)
  - `PropagationJobWorker` (background executor claiming jobs and managing lease heartbeats)
  - Job models: `PropagationJob`, `PropagationJobStatus`.

### Phase 4: Risk Propagation Engine & Transactional Outbox Alerting (`:fraud`)
- [x] `TASK-4.1` [RED]: Write unit/integration test `DefaultRiskPropagationEngineTest` verifying:
  - Full propagation cycle from high-risk sources evaluated as of deterministic `as_of`.
  - Combining path discovery, influence calculation, and probabilistic aggregation.
  - Persistence of calculated `propagated_risk` without altering `direct_risk` (`I-PROP-005`).
  - Staging `EntityRiskPropagationDetectedEvent` in transactional `outbox` table within the same DB transaction (`I-OUTBOX-001`, `I-PROP-009`) when $R_{\text{propagated}} \ge \theta_{\text{alert}}$.
- [x] `TASK-4.2` [GREEN]: Implement `DefaultRiskPropagationEngine` (implementing public interface `RiskPropagationEngine` in `br.com.wallet.fraud.intelligence.propagation`).
- [x] `TASK-4.3` [GREEN]: Define Spring Modulith `@NamedInterface("api")` in `br.com.wallet.fraud.intelligence.propagation.package-info.java`.

### Phase 5: REST Exposure, Outbox Relay & Verification Gate (`:` root)
- [x] `TASK-5.1` [RED]: Write MockMvc test `FraudRiskPropagationControllerTest`.
- [x] `TASK-5.2` [GREEN]: Implement `FraudRiskPropagationController` (`POST /api/v1/fraud/intelligence/propagation/evaluate/{entityId}`, `GET /api/v1/fraud/intelligence/propagation/entity/{entityId}`) and register `EntityRiskPropagationDetectedEvent` in `DomainEventType` for OutboxRelay publishing to NATS `events.fraud.propagation`.
- [x] `TASK-5.3`: Execute `ModulithArchitectureTest.verifyArchitecture()` to ensure zero architectural boundary violations.
- [x] `TASK-5.4`: Run full test suite `./gradlew test` with Testcontainers.
- [x] `TASK-5.5`: Verify seed data fixtures and practical test scenarios (cURL, SQL, NATS) in `SUMMARY-000.6-fraud-risk-propagation-and-temporal-decay.md` per `I-SDD-002`.

---

## 3. Practical Verification Scenarios (Seed Data Gate)

1. **Scenario 1: Single-Hop Hardware Link Decay**:
   - Alice (`direct_risk = 0.90`) sharing device `D1` with Bob 7 days prior to `as_of` propagates $I = 0.90 \times 0.90 \times 0.50 = 0.405$ to Bob.
2. **Scenario 2: Multi-Hop Mule Money Trail**:
   - Risk from Wallet 3 (`direct_risk = 0.90`) flows along transfer edges with elapsed decay evaluated as of deterministic `as_of`.
3. **Scenario 3: Multi-Path Union (Device + Transfer)**:
   - Entity connected via both `SHARED_DEVICE` ($I_1 = 0.40$) and `TRANSFERRED_TO` ($I_2 = 0.30$) receives $R = 1 - (1 - 0.40)(1 - 0.30) = 0.58$.
4. **Scenario 4: Token Lease & Uniqueness Enforcement**:
   - Verify PostgreSQL unique constraint rejects duplicate active jobs, and worker token lease prevents split-brain duplicate execution.
5. **Scenario 5: Transactional Outbox Alerting**:
   - Query `outbox` table to verify `EntityRiskPropagationDetectedEvent` staged atomically, and verify delivery to NATS topic `events.fraud.propagation`.
