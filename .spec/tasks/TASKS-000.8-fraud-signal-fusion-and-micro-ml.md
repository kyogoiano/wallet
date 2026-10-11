# 📝 Task Breakdown: TASKS-000.8 — Fraud Signal Fusion, Micro-ML & Hand-Rolled Investigation Orchestration

- **Associated Spec**: [`../SPEC-000.8-fraud-signal-fusion-and-micro-ml.md`](file:///.spec/SPEC-000.8-fraud-signal-fusion-and-micro-ml.md)
- **Associated Plan**: [`../plans/PLAN-000.8-fraud-signal-fusion-and-micro-ml.md`](file:///.spec/plans/PLAN-000.8-fraud-signal-fusion-and-micro-ml.md)
- **Status**: Ready for Implementation (Stage 4 — Tasks Ratified with History 34)
- **Author**: Antigravity Financial & Risk Engineering Team
- **Date**: 2026-09-08
- **Target Release**: Wallet Service V4.x — Phase 0.8

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-FUSION-001` (Signal Fusion with Groups) | `RiskFusionEngineTest.shouldFuseSignalsWithCorrelationGroups()` | `TASK-1.1`, `TASK-1.2` |
| `I-FUSION-001` (Direct Rule Primacy Override) | `RiskFusionEngineTest.shouldOverrideWithHardBlockWhenDirectViolated()` | `TASK-1.1`, `TASK-1.2` |
| `I-FUSION-002` (Monotonic Correlated Bounding) | `RiskFusionEngineTest.shouldEnforceMonotonicBounds()` | `TASK-1.1`, `TASK-1.2` |
| `I-FUSION-003` & `REQ-FUSION-010` (LOO Attribution) | `RiskFusionEngineTest.shouldComputeLeaveOneOutMarginalAttribution()` | `TASK-1.1`, `TASK-1.2` |
| `I-FUSION-010` (Observable ML Degradation) | `RiskFusionEngineTest.shouldGracefullyDegradeWhenMlUnavailable()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-FUSION-004` (PostgreSQL Durable Job Queue)| `PostgresFusionJobDaoIT.shouldAcquireAndCompleteJobWithSkipLocked()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-FUSION-009` (Job Coalescing via Partial Index) | `PostgresFusionJobDaoIT.shouldCoalesceMultiplePendingEventsIntoLatestAsOf()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-FUSION-013` & `I-FUSION-009` (Lease Recovery) | `FusionJobRecoveryServiceTest.shouldReclaimExpiredRunningJobsWithBackoff()` | `TASK-2.1`, `TASK-2.2` |
| `I-FUSION-005` (Zero Hot-Path ML Training) | `PostgresFusionJobDaoIT.shouldExecuteAsynchronouslyViaQueue()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-FUSION-003` & `I-FUSION-004` (Embedded ONNX) | `OnnxRiskModelEvaluatorTest.shouldEvaluateTabularFeatures()` | `TASK-3.1`, `TASK-3.2` |
| `I-FUSION-008` & `REQ-FUSION-012` (Feature Schema Version) | `OnnxRiskModelEvaluatorTest.shouldEnforceFeatureSchemaVersionMatch()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-FUSION-005` & `REQ-FUSION-011` (Decision Policy) | `RiskDecisionPolicyTest.shouldDifferentiateHardBlockFromRestrict()` | `TASK-4.1`, `TASK-4.2` |
| `I-FUSION-007` (Investigation Reuse Gate) | `InvestigationDispatcherTest.shouldInvokePhase07InvestigationService()` | `TASK-4.1`, `TASK-4.2` |
| `REQ-FUSION-008` (Analyst Feedback Audit) | `PostgresCheckpointDaoIT.shouldStoreAndOverrideCheckpoints()` | `TASK-4.1`, `TASK-4.2`, `TASK-4.3` |
| `REQ-FUSION-006` (Typed Subject Hot State Store) | `RedisRiskProfileStoreIT.shouldMaterializeAndExpireProfile()` | `TASK-5.1`, `TASK-5.2` |
| `I-FUSION-006` (Storage-Agnostic Abstraction) | `RedisRiskProfileStoreIT.shouldOperateThroughStoreInterface()` | `TASK-5.1`, `TASK-5.2` |
| `REQ-FUSION-007` (Fraud Gate V4 Authorization) | `FraudGateV4IT.shouldAuthorizeOrBlockWithinGatewaySla()` | `TASK-5.1`, `TASK-5.2` |
| **End-to-End Orchestration** | `FraudSignalFusionWorkflowIT.shouldExecuteEndToEndPipeline()` | `TASK-6.1` |
| **ONNX Performance Benchmark** | `OnnxRiskModelBenchmark.runBenchmark()` (P95 < 2ms) | `TASK-6.2` |

---

## 2. Implementation Tasks (TDD Order per History 34)

### Phase 1: Pure Mathematical Multi-Signal Fusion, Correlation Groups & LOO Attribution (`br.com.wallet.fraud.fusion`)
- [x] `TASK-1.1` [RED]: Write unit tests in `RiskFusionEngineTest` (Zero Spring, Zero Docker):
  - Direct rule primacy override ($R_{\text{direct}} \ge 1.0 \implies \text{HARD\_BLOCK}$, `primaryDriver = DIRECT_HARD_RULE`).
  - Graph Correlation Group: $R_{\text{graph-group}} = 1 - (1 - R_{\text{graph}}) \cdot (1 - w_p R_{\text{propagated}})$.
  - Master Fusion with ML present: $R_{\text{final}} = 1 - (1 - R_{\text{direct}}) \cdot (1 - w_g R_{\text{graph-group}}) \cdot (1 - w_b R_{\text{behavioral}}) \cdot (1 - w_m R_{\text{ML}})$.
  - Master Fusion with ML unavailable (`I-FUSION-010`): degrades gracefully by omitting the ML factor and recording `degradedReason = "ONNX_MODEL_UNAVAILABLE"`.
  - Monotonic bounding in $[0.0, 1.0]$.
  - Leave-One-Out (LOO) marginal risk attribution ($C_s = \max(0.0, R_{\text{final}} - R_{\text{final without } s})$) and normalized percentages ($\sum C'_s = 100\% \pm 10^{-9}$).
- [x] `TASK-1.2` [GREEN]: Implement domain records and classes: `FraudSignalSet`, `RiskFusionWeights`, `RiskFusionResult`, `RiskAttribution`, `FactorContribution`, `ProbabilisticRiskFusionEngine`, `RiskCorrelationGroup`, `GraphIntelligenceCorrelationGroup`, and `RiskAttributionCalculator`.
- [x] `TASK-1.3` [REFACTOR]: Optimize calculations and verify absolute immutability. Run unit tests via `./gradlew :fraud:test --tests "*RiskFusionEngineTest"`.

### Phase 2: PostgreSQL Durable Job Queue with Partial Index Coalescing & Lease Recovery (`br.com.wallet.fraud.fusion.internal.orchestration`)
- [x] `TASK-2.1` [RED]: Write integration test `PostgresFusionJobDaoIT` and unit test `FusionJobRecoveryServiceTest`:
  - Test `shouldCoalesceMultiplePendingEventsIntoLatestAsOf`: multiple events for a `PENDING` entity coalesce into a single job updating `as_of` and `payload`.
  - Test events arriving while `RUNNING` cleanly insert a new `PENDING` job for subsequent execution.
  - Test worker acquisition using `SELECT FOR UPDATE SKIP LOCKED`.
  - Test `FusionJobRecoveryService`: detects expired `RUNNING` jobs (`lease_expires_at < NOW()`) and applies backoff (`5s`, `30s`, `2m`, `10m`) up to `MAX_ATTEMPTS = 5` before marking `FAILED`.
- [x] `TASK-2.2` [GREEN]: Add DDL in `../../docker/init/schema.sql` for `fraud_fusion_jobs` with partial unique index `CREATE UNIQUE INDEX uq_fusion_job_pending_entity ON fraud_fusion_jobs (entity_id) WHERE status = 'PENDING'`. Implement `PostgresFusionJobDao`, `FusionEvaluationDispatcher`, `FusionJobWorker`, and `FusionJobRecoveryService`.
- [x] `TASK-2.3` [REFACTOR]: Optimize transaction isolation and polling indexes.

### Phase 3: Embedded Pure Java ONNX Micro-ML & Feature Versioning (`br.com.wallet.fraud.fusion.internal.ml`)
- [x] `TASK-3.1` [RED]: Write unit tests in `OnnxRiskModelEvaluatorTest`:
  - Feature vector schema validation (`I-FUSION-008`): throws `IncompatibleFeatureSchemaException` on version mismatch or count mismatch.
  - Observable degradation (`I-FUSION-010`): returns `MlRiskResult.Unavailable` when model is missing or fails (no synthetic proxies).
  - Basic scoring test with valid feature vector.
- [x] `TASK-3.2` [GREEN]: Add dependency `com.microsoft.onnxruntime:onnxruntime` to `../../fraud/build.gradle`. Implement `OnnxModelLoader`, `FraudFeatureMapper`, `MlFeatureVector`, `OnnxModelMetadata`, `MlRiskResult`, and `OnnxRiskModelEvaluator`. Place sample pre-trained tabular ONNX model in `fraud/src/main/resources/models/fraud_risk_tabular_v1.onnx`.
- [x] `TASK-3.3` [BENCHMARK]: Implement `OnnxRiskModelBenchmark` (dedicated performance gate task) verifying P95 $< 2\text{ms}$ on CPU.

### Phase 4: Differentiated Decision Policy & Phase 0.7 Investigation Delegation (`br.com.wallet.fraud.fusion.internal.policy`)
- [x] `TASK-4.1` [RED]: Write unit tests in `RiskDecisionPolicyTest` and `InvestigationDispatcherTest`:
  - 4-state routing: `HARD_BLOCK` ($R_{\text{direct}} \ge 1.0$), `RESTRICT` ($R_{\text{final}} \ge 0.85$), `REVIEW` ($[0.50, 0.85)$), and `ALLOW` ($< 0.50$).
  - Under `REVIEW` and `RESTRICT`: dispatches investigation to Phase 0.7 `InvestigationService`.
- [x] `TASK-4.2` [GREEN]: Implement `RiskDecisionPolicy`, `FraudDecision` enum, `InvestigationDispatcher`, and `PostgresCheckpointDao` (`fraud_investigation_checkpoints` and `fraud_analyst_reviews` DDL in `schema.sql`).
- [x] `TASK-4.3` [GREEN]: Implement REST controller endpoint `AnalystReviewController` (`POST /api/v1/fraud/intelligence/fusion/reviews/{checkpointId}`) allowing compliance analysts to record overrides (`REQ-FUSION-008`).

### Phase 5: Typed Subject Hot State Materialization & Fraud Gate V4 (`br.com.wallet.fraud.fusion.internal.persistence` & `gate`)
- [x] `TASK-5.1` [RED]: Write integration tests in `RedisRiskProfileStoreIT` and `FraudGateV4IT`:
  - `RiskSubject` key format `risk_profile:{type}:{id}`.
  - Contextual degradation policy (`GateDegradationPolicy`): fail-closed for high risk, deterministic fallback on cache failure.
  - Payment authorization lookup under gateway SLA (P99 $< 2\text{ms}$).
- [x] `TASK-5.2` [GREEN]: Implement `RiskSubject`, `RiskSubjectType`, `RiskProfileStore`, `RedisRiskProfileStore` using Lettuce, and `FraudGateV4`.
- [x] `TASK-5.3` [REFACTOR]: Verify TTL 3600s expiration and metrics instrumentation.

### Phase 6: End-to-End Workflow Integration & Convergence
- [x] `TASK-6.1` [RED/GREEN]: Implement `FraudSignalFusionWorkflowIT` executing the end-to-end flow from event trigger to durable worker, ONNX inference, fusion calculation, decision routing, Phase 0.7 investigation, and hot cache storage.
- [x] `TASK-6.2` [AUDIT]: Run Spring Modulith verification (`ModulithArchitectureTest`) ensuring zero architectural cycle violations.
- [x] `TASK-6.3` [DOC]: Author `SUMMARY-000.8-fraud-signal-fusion-and-micro-ml.md` with Practical Verification Guide and seed fixtures (`I-SDD-002`).

---

## 3. Convergence & Verification Checklist

- [x] All unit tests pass: `./gradlew test`
- [x] All integration tests pass: Testcontainers suite green
- [x] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`)
- [x] Zero compiler / linter warnings
- [x] OpenTelemetry traces verified
- [x] Seed data added/updated in `../../docker/init/schema.sql`
- [x] Author Practical Verification Guide & Seed Data in `SUMMARY-000.8.md` (`I-SDD-002`)
- [x] Traceability report generated: 100% of requirements verified
