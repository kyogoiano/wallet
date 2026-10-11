# 📝 Task Breakdown: TASKS-000.8.1 — Native Typed AI Decision Algebra & Grounded Evaluation Topology

- **Associated Spec**: [`../SPEC-000.8.1-typed-ai-decision-evaluation.md`](file:///.spec/SPEC-000.8.1-typed-ai-decision-evaluation.md)
- **Associated Architecture**: [`../architecture/ARCH-000.8.1-native-typed-decision-algebra.md`](file:///.spec/architecture/ARCH-000.8.1-native-typed-decision-algebra.md)
- **Associated Plan**: [`../plans/PLAN-000.8.1-typed-ai-decision-evaluation.md`](file:///.spec/plans/PLAN-000.8.1-typed-ai-decision-evaluation.md)
- **Status**: ✅ **Completed**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant / Triad | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-TYPED-001`, `I-TYPED-003`, Triad 2 | `[MUST]` | `EvaluationResultTypeBindingTest` | `TASK-TYPED-1.1`, `TASK-TYPED-1.2`, `TASK-TYPED-1.3`, `TASK-TYPED-2.2` |
| `REQ-TYPED-002`, `REQ-TYPED-014`, `REQ-TYPED-016` | `[MUST]` | `FiveGateBenchmarkTest` | `TASK-TYPED-6.1`, `TASK-TYPED-6.2` |
| `REQ-TYPED-003` | `[MUST]` | `FraudDecisionQuestionsTest` | `TASK-TYPED-3.1` |
| `REQ-TYPED-004`, `I-TYPED-004` | `[MUST]` | `DecisionEvidenceTest` | `TASK-TYPED-2.1` |
| `REQ-TYPED-005` | `[MUST]` | `ConfidenceCalibrationTest` | `TASK-TYPED-1.3`, `TASK-TYPED-6.1` |
| `REQ-TYPED-006`, `I-TYPED-006`, Triad 4 | `[MUST]` | `DecisionComposerTest` | `TASK-TYPED-4.1`, `TASK-TYPED-4.2` |
| `REQ-TYPED-007`, `I-TYPED-001`, `I-TYPED-002`, Triad 1 | `[MUST]` | `DecisionBoundaryArchitectureTest` | `TASK-TYPED-7.1` |
| `REQ-TYPED-008` | `[SHOULD]` | `OllamaDecisionEvaluatorTest` | `TASK-TYPED-5.1` |
| `REQ-TYPED-009` | `[SHOULD]` | `DirectedMarginBenchmarkTest` | `TASK-TYPED-6.1`, `TASK-TYPED-6.2` |
| `REQ-TYPED-010`, `REQ-TYPED-011` | `[WON'T]` | `DecisionBoundaryArchitectureTest` (zero AI framework deps, zero hot-path calls) | `TASK-TYPED-7.1` |
| `REQ-TYPED-012` | `[WON'T]` | `DecisionComposerTest` (refusal to coerce unavailable to synthetic values) | `TASK-TYPED-4.1`, `TASK-TYPED-4.2` |
| `REQ-TYPED-013`, `I-TYPED-005`, Triad 3 | `[MUST]` | `DecisionUnavailableStructureTest`, `DecisionEvaluatorTimeoutTest` | `TASK-TYPED-1.3`, `TASK-TYPED-5.2` |
| `REQ-TYPED-015` | `[MUST]` | `EvaluationResultTypeBindingTest` | `TASK-TYPED-1.2`, `TASK-TYPED-2.2` |
| `REQ-TYPED-017` | `[MUST]` | `InvestigationDispatcherIntegrationTest` | `TASK-TYPED-8.1`, `TASK-TYPED-8.2` |
| `REQ-TYPED-018` | `[MUST]` | `FraudInvestigationControllerTest` | `TASK-TYPED-8.3` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing a task, focus strictly on the active task card below. Do not load unrelated modules into memory.

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Native Decision Values & Sealed Outcome Primitives (`:fraud`)
- [x] `TASK-TYPED-1.1` [MUST]: Implement `DecisionValue` sealed interface and concrete records in `br.com.wallet.fraud.decision.model`:
  - `public sealed interface DecisionValue permits BooleanDecision, ScoreDecision, CategoryDecision, TextDecision, MultiSelectDecision`
  - `BooleanDecision(boolean value, String rationale)`
  - `ScoreDecision(BigDecimal score, String rationale)` enforcing non-null, scale 2, and range $[0.00, 1.00]$
  - `CategoryDecision(String category, String rationale)`
  - `TextDecision(String summary)`
  - `MultiSelectDecision(Set<String> selected, String rationale)`
  - Implement unit test `DecisionValueTest` validating range boundary checks, immutability, and exact `BigDecimal` scale (`I-TDD-002`).
- [x] `TASK-TYPED-1.2` [MUST]: Implement `DecisionQuestion<T extends DecisionValue>` record:
  - Fields: `(String questionId, String questionKey, String description, Class<T> valueType)`
  - Compact constructor enforcing non-null fields and runtime type-witness presence (`REQ-TYPED-015`).
  - Implement `DecisionQuestionTest`.
- [x] `TASK-TYPED-1.3` [MUST]: Implement sealed `DecisionOutcome<T extends DecisionValue>` hierarchy:
  - `public sealed interface DecisionOutcome<T extends DecisionValue> permits DecisionAnswer, DecisionUnavailable`
  - `Confidence`: enum (`HIGH`, `MEDIUM`, `LOW`) with probability range mappings (`REQ-TYPED-005`).
  - `UnavailableReason`: enum (`TIMEOUT`, `PROVIDER_UNAVAILABLE`, `RATE_LIMITED`, `PARSE_FAILURE`, `INSUFFICIENT_EVIDENCE`).
  - `DecisionProvenance`: record `(String modelIdentifier, String promptVersion, Instant timestamp, Long latencyMs)`.
  - `DecisionAnswer<T extends DecisionValue>(T value, Confidence confidence, List<DecisionEvidence> grounding, DecisionProvenance provenance)`.
  - `DecisionUnavailable<T extends DecisionValue>(UnavailableReason reason, String diagnosticMessage, DecisionProvenance provenance)`.
  - **Structural Invariant**: `DecisionUnavailable<T>` structurally exposes NO `score()` or `confidence()` accessors (`REQ-TYPED-013`, `I-TYPED-005`).
  - Implement `DecisionOutcomeTest` verifying structural absence of score/confidence and immutability.

### Phase 2: Machine-Verifiable Evidence & Generic Type Binding (`:fraud`)
- [x] `TASK-TYPED-2.1` [MUST]: Implement `DecisionEvidence` with canonical JSON SHA-256 hashing (`REQ-TYPED-004`, `I-TYPED-004`):
  - Record: `(String evidenceKey, String summary, Map<String, Object> facts, String contentHash)`.
  - Static factory: `DecisionEvidence.of(String key, String summary, Map<String, Object> facts)` computing canonical lexicographically sorted JSON and SHA-256 digest.
  - Implement `DecisionEvidenceTest`: assert identical content hash regardless of input map key order; assert hash mutation upon fact modification.
- [x] `TASK-TYPED-2.2` [MUST]: Implement `EvaluatedQuestion<T>` and `EvaluationResult` with runtime type witness verification:
  - `EvaluatedQuestion<T extends DecisionValue>(DecisionQuestion<T> question, DecisionOutcome<T> outcome)`.
  - `EvaluationResult(String evaluationId, String subjectId, List<EvaluatedQuestion<?>> questions, Instant evaluatedAt)`.
  - Generic accessor: `<T extends DecisionValue> DecisionOutcome<T> outcomeFor(DecisionQuestion<T> question)`.
  - Create `br.com.wallet.fraud.decision.evaluator.MismatchedDecisionTypeException`.
  - Implement `EvaluationResultTypeBindingTest` (Test Triad 2):
    - Positive: extract `BooleanDecision` outcome for `DecisionQuestion<BooleanDecision>`.
    - Negative: mismatched runtime type witness throws `MismatchedDecisionTypeException`.
    - Compile-time assertion: mismatched type assignment cannot compile.

### Phase 3: Standard Fraud Domain Catalog (`:fraud`)
- [x] `TASK-TYPED-3.1` [MUST]: Implement `FraudDecisionQuestions` catalog in `br.com.wallet.fraud.decision.catalog` (`REQ-TYPED-003`):
  - `BEHAVIOR_ANOMALY`: `DecisionQuestion<BooleanDecision>` ("Is current transaction pattern anomalous vs 30-day baseline?").
  - `SUSPECTED_MULE_RING`: `DecisionQuestion<BooleanDecision>` ("Does entity topology indicate mule account orchestration?").
  - `ANOMALOUS_CASH_OUT`: `DecisionQuestion<ScoreDecision>` ("Quantify rapid cash-out dissipation risk in range [0.00, 1.00]").
  - `INVESTIGATION_SUMMARY`: `DecisionQuestion<TextDecision>` ("Synthesized human-readable investigation narrative").
  - Implement `FraudDecisionQuestionsTest` asserting stable question keys, unique IDs, and valid type witnesses.

### Phase 4: Anti-Coercion Decision Composer (`:fraud`)
- [x] `TASK-TYPED-4.1` [MUST]: Implement `DecisionComposer` and `CompositionPolicy` in `br.com.wallet.fraud.decision.composition` (`REQ-TYPED-006`, `I-TYPED-006`):
  - `CompositionPolicy`: `FAIL_CLOSED`, `REQUIRE_MANDATORY_QUESTIONS`, `DEGRADE_TO_UNVERIFIED`.
  - `CompoundRiskAssessment`: record `(AssessmentStatus status, Set<String> triggeredSignals, List<DecisionUnavailable<?>> unavailables, String explanation)`.
  - Anti-coercion rule: `DecisionUnavailable` CANNOT be coerced into `false`, `0.00`, or synthetic default scores (`REQ-TYPED-012`).
- [x] `TASK-TYPED-4.2` [MUST]: Implement `DecisionComposerTest` (Test Triad 4):
  - Positive: all questions present and answered -> computes deterministic `CompoundRiskAssessment`.
  - Negative: required question unavailable under `FAIL_CLOSED` returns `AssessmentStatus.INCONCLUSIVE` without proceeding.
  - Boundary: assert that unavailable `BooleanDecision` does not default to `false` and unavailable `ScoreDecision` does not default to `0.00`.

### Phase 5: Provider-Neutral Evaluator SPI & Timeout Containment (`:fraud`)
- [x] `TASK-TYPED-5.1` [SHOULD]: Implement `DecisionEvaluator` SPI and `OllamaDecisionEvaluator` in `br.com.wallet.fraud.decision.evaluator` (`REQ-TYPED-008`):
  - Interface: `CompletableFuture<EvaluationResult> evaluate(String subjectId, List<DecisionQuestion<?>> questions, Map<String, DecisionEvidence> evidence)`.
  - Implementation: `OllamaDecisionEvaluator` invoking `LocalInferenceClient` (Ollama SLM) with structured prompt formatting.
  - Error translation: Catch-blocks wrap timeouts and inference failures into `DecisionUnavailable<T>` records (`I-TYPED-005`). Zero unchecked exceptions thrown to callers.
- [x] `TASK-TYPED-5.2` [MUST]: Implement `DecisionEvaluatorTimeoutTest` (Test Triad 3):
  - Positive: evaluator completes within timeout -> returns `DecisionAnswer<ScoreDecision>` with valid score.
  - Boundary: simulate evaluator timeout -> returns `DecisionUnavailable<ScoreDecision>` with structural absence of score/confidence and zero synthetic values materialized.

### Phase 6: Five-Gate Semantic Benchmark Harness (`:fraud`)
- [x] `TASK-TYPED-6.1` [MUST]: Implement `FiveGateBenchmarkHarness` in `br.com.wallet.fraud.decision.benchmark` (`REQ-TYPED-002`, `REQ-TYPED-014`, `REQ-TYPED-016`):
  - Gate 1: Contract Adherence ($100\%$ type & schema match, zero malformed JSON).
  - Gate 2: Grounding & Provenance ($100\%$ of grounding claims match `DecisionEvidence.contentHash`).
  - Gate 3: Task Correctness with directed non-inferiority margin:
    $$\text{F1}_{\text{candidate}} \ge \text{F1}_{\text{baseline}} - \epsilon_{\text{workload}} \quad (\epsilon = 0.02)$$
  - Gate 4: Expected Calibration Error:
    $$\text{ECE}_{\text{candidate}} \le \text{ECE}_{\text{baseline}} + \epsilon_{\text{calib}} \quad (\epsilon = 0.05)$$
  - Gate 5: Operational Performance & Determinism ($100\%$ identical replays at temperature $= 0$, $P95 \le \text{budget}$).
- [x] `TASK-TYPED-6.2` [MUST]: Implement `FiveGateBenchmarkTest`:
  - Run benchmark harness against synthetic evaluation dataset.
  - Assert that candidate passing all five gates receives certification.
  - Assert that candidate failing Gate 3 (regression beyond $\epsilon$) or Gate 2 (hallucinated evidence hash) fails immediately with detailed gate diagnostic.

### Phase 7: Modulith Architectural Airgap Verification (Test Triad 1)
- [x] `TASK-TYPED-7.1` [MUST]: Implement `DecisionBoundaryArchitectureTest` in `br.com.wallet` (`REQ-TYPED-007`, `I-TYPED-001`, `I-TYPED-002`, `REQ-TYPED-010`):
  - Assert `br.com.wallet.ledger..*` has 0 dependencies on `br.com.wallet.fraud.decision..*`.
  - Assert `br.com.wallet.fraud.fusion.gate..*` has 0 dependencies on `br.com.wallet.fraud.decision..*`.
  - Assert `br.com.wallet.fraud.decision..*` has 0 dependencies on external third-party AI libraries (e.g. `spring-ai-typesafe`).
  - Verify Core transfer execution completes in $<15\text{ms}$ with fraud gate authorization $P99 < 2\text{ms}$ without invoking `DecisionEvaluator`.

### Phase 8: Nearline Flow Attachment & Human-in-the-Loop Integration
- [x] `TASK-TYPED-8.1` [MUST]: Register `DecisionComposer` as Spring `@Component` and configure `DecisionEvaluator` Spring bean in `:fraud` (`REQ-TYPED-008`, `REQ-TYPED-017`).
- [x] `TASK-TYPED-8.2` [MUST]: Integrate `DecisionEvaluator` & `DecisionComposer` into `InvestigationDispatcher`:
  - When fusion triggers `REVIEW` or `RESTRICT`, build `DecisionEvidence` from fusion signals and invoke `DecisionEvaluator.evaluate()` nearline.
  - Run `DecisionComposer.compose()` under `DEGRADE_TO_UNVERIFIED` and serialize `CompoundRiskAssessment` into `fraud_investigation_checkpoints`.
  - Update `InvestigationDispatcherTest` asserting structured evaluation persistence without mutating deterministic fusion ($R_{\text{final}}$) (`REQ-TYPED-017`).
- [x] `TASK-TYPED-8.3` [MUST]: Expose typed decision evaluation in `FraudInvestigationController` & `FraudInvestigationApi`:
  - Add `POST /api/v1/fraud/intelligence/investigation/{entityId}/evaluate` and `GET /api/v1/fraud/intelligence/investigation/{entityId}/decisions` allowing analysts to inspect grounded verdicts with SHA-256 evidence hashes.
  - Update `FraudInvestigationControllerTest` (`REQ-TYPED-018`).

---

## 4. Practical Verification Guide (`I-SDD-002`)

```bash
# 1. Run unit test suite for native decision algebra and anti-coercion gate
./gradlew :fraud:test --tests "br.com.wallet.fraud.decision.model.*"
./gradlew :fraud:test --tests "br.com.wallet.fraud.decision.composition.*"

# 2. Run evaluator timeout and structural absence tests (Triad 3)
./gradlew :fraud:test --tests "br.com.wallet.fraud.decision.evaluator.*"

# 3. Run Five-Gate benchmark harness
./gradlew :fraud:test --tests "br.com.wallet.fraud.decision.benchmark.*"

# 4. Run Spring Modulith architectural airgap verification (Triad 1)
./gradlew :test --tests "br.com.wallet.DecisionBoundaryArchitectureTest"

# 5. Run nearline dispatcher and human-in-the-loop REST tests
./gradlew :test --tests "br.com.wallet.unit.fraud.fusion.InvestigationDispatcherTest"
./gradlew :test --tests "br.com.wallet.unit.infrastructure.rest.FraudInvestigationControllerTest"

# 6. Verify Human-in-the-Loop REST API (cURL)
curl -X POST http://localhost:8080/api/v1/fraud/intelligence/investigation/00000000-0000-0000-0000-000000000001/evaluate
curl -X GET  http://localhost:8080/api/v1/fraud/intelligence/investigation/00000000-0000-0000-0000-000000000001/decisions
```
