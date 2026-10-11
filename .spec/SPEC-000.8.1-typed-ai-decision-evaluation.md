# 📐 Specification: SPEC-000.8.1 — Native Typed Decision Algebra & Semantic Evaluation Engine

- **Status**: 🟡 **Proposed (Ratification Ready per History 66)**
- **Author**: Antigravity Financial AI & Risk Intelligence Guild
- **Date**: 2026-09-22
- **Source Reference**: [`.histories/history61.txt`](file:///.histories/history61.txt), [`.histories/history62.txt`](file:///.histories/history62.txt), [`.histories/history63.txt`](file:///.histories/history63.txt), [`.histories/history64.txt`](file:///.histories/history64.txt), [`.histories/history65.txt`](file:///.histories/history65.txt), [`.histories/history66.txt`](file:///.histories/history66.txt)
- **Target Release**: Wallet Service V4 — Phase 000.8.1
- **Bounded Context**: Fraud Intelligence, Decision Layer & SLM Investigation (`:fraud` — `fusion`, `investigation`, `embeddings`)
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly scoped to native domain algebra, provider-neutral SPI, evaluation composition, and benchmark gates.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history64.txt`](file:///.histories/history64.txt): *"Steal the architecture, not the library."* Forbade adding `spring-ai-typesafe` as a dependency in V4. Mandated native typed decision algebra in pure Java.
  - [`.histories/history65.txt`](file:///.histories/history65.txt): Defined generic question-outcome binding, outcome partitioning, absent confidence on unavailable outcomes, and Five-Gate Evaluation Protocol.
  - [`.histories/history66.txt`](file:///.histories/history66.txt): Ratified final type-system and mathematical rigor:
    1. `EvaluationResult` structurally preserves `Question<T> -> Outcome<T>` via `EvaluatedQuestion<T>`.
    2. Directed `I-TYPED-004` inequalities for maximize vs. minimize metrics.
    3. Explicit `DecisionUnavailable<T>` generic typing carrying absent confidence.
    4. Formulated `I-TYPED-006` prohibiting implicit unavailable-to-false/zero coercion in `DecisionComposer`.
    5. Restored determinism/reproducibility metrics and throughput to Gate 5.
    6. Mandated runtime type-witness validation (`Class<T>`) against generic parameters.
    7. Normalized `EvidenceReference` with cryptographic content hash.
    8. Mandated rubric version changes invalidate historical benchmark comparability.
  - [`constitution.md`](file:///.agents/rules/constitution.md): `I-FRAUD-001` (Pre-execution gate, zero ledger mutation), `I-FRAUD-002` ($O(1)$ Hot Path, P99 $< 2\text{ms}$).

---

## 1. Intent & Business Value

Generative model integrations may exhibit output variability, unsupported claims, schema violations, and latency variability when structured decision contracts are enforced through free-form prose generation. Evaluating **atomic typed questions over a single immutable evidence state** provides vastly superior testability, auditability, and reproducibility.
Per **Histories 64, 65 & 66**, this specification establishes a **Native Typed Decision Algebra & Semantic Evaluation Engine**:
1. **Domain-Owned Typed Algebra**: Pure Java domain abstractions (`DecisionState`, `DecisionQuestion<T extends DecisionValue>`, `DecisionOutcome<T>`, `EvaluationResult`, `DecisionEvaluator` SPI, `DecisionComposer`) with zero external AI framework imports.
2. **Generic Structural Binding**: `EvaluatedQuestion<T>` binds `DecisionQuestion<T>` to `DecisionOutcome<T>` at compile time, validated at runtime via `Class<T>` type witnesses.
3. **Outcome Partitioning & Coercion Gate**: Decouples `DecisionAnswer<T>` from `DecisionUnavailable<T>`. When an evaluator fails, confidence is strictly absent (`Confidence = ABSENT`). `DecisionComposer` is strictly prohibited from coercing unavailable outcomes into false, zero, or synthetic defaults (`I-TYPED-006`).
4. **Hot-Path Airgap Invariant**: Financial authorization ($P99 < 2\text{ms}$) remains strictly governed by deterministic rules, sub-millisecond ONNX micro-ML, and DragonflyDB hot state. Typed evaluations run strictly nearline/offline.
5. **Five-Gate Evaluation Protocol**: Benchmarks candidate engines on identical labeled tasks across Contract Validity, Evidence Grounding, Predictive Correctness, Calibration, and Operational Performance/Determinism.

---

## 2. Mathematical & System Invariants

- **`I-TYPED-001` (Hot-Path Airgap Invariant)**: Generative models, remote LLM APIs, and external SLM inference engines SHALL NEVER execute on the synchronous transaction path:
  $$\text{Deps}(\text{SynchronousTxPath}) \cap \{\text{LLM}, \text{SLM}, \text{Jevify}, \text{Ollama}, \text{SpringAI}\} = \emptyset$$
- **`I-TYPED-002` (Hot-Path Authorization SLA vs. Transfer Budget)**: Hot-path fraud authorization SLA is $P99 < 2.0\text{ms}$, strictly decoupled from Core end-to-end transfer latency ($< 15\text{ms}$):
  $$\text{AuthDecision} = f(\text{Rules}, \text{ONNX}, \text{DragonflyRiskProfile}), \quad T_{\text{auth}}(P99) \le 2.0\text{ms}, \quad T_{\text{core\_transfer}} \le 15.0\text{ms}$$
- **`I-TYPED-003` (Strongly Typed Structural Binding)**: Every question typed to $T$ produces an outcome bound to $T$:
  $$\forall Q \in \text{DecisionQuestion}\langle T \rangle, \quad \text{Outcome}(Q) \in \text{DecisionOutcome}\langle T \rangle, \quad T \in \{\text{BooleanDecision}, \text{ScoreDecision}, \text{ChoiceDecision}\}$$
  $$\text{DecisionOutcome}\langle T \rangle = \text{DecisionAnswer}\langle T \rangle \cup \text{DecisionUnavailable}\langle T \rangle$$
- **`I-TYPED-004` (Task-Equivalent Non-Inferiority with Directed Margin)**: A candidate engine SHALL NOT be adopted unless it satisfies the pre-declared margin $\epsilon_{\text{workload}}$ along the metric's optimization direction:
  $$\text{Cand} \ge \text{Base} - \epsilon_{\text{workload}} \quad (\text{maximize: Precision, Recall, F1, AUROC}), \quad \text{Cand} \le \text{Base} + \epsilon_{\text{workload}} \quad (\text{minimize: Brier, Calibration Error, Latency})$$
- **`I-TYPED-005` (Unavailable Decision Semantics & Absent Confidence)**: When a semantic evaluator is unavailable, the outcome is `DecisionUnavailable<T>`. Confidence is strictly absent:
  $$\text{InferenceUnavailable} \implies \text{Outcome} = \text{DecisionUnavailable}\langle T \rangle(\text{reason}), \quad \text{Confidence} = \text{ABSENT}$$
- **`I-TYPED-006` (No Implicit Unavailable Coercion)**: `DecisionComposer` MUST NOT implicitly convert `DecisionUnavailable<T>` into false, zero, default score, or any synthetic value:
  $$\forall u \in \text{DecisionUnavailable}\langle T \rangle, \quad \text{DecisionComposer}(u) \ne \text{DecisionValue}, \quad \text{Coercion}(u \to \{\text{false}, 0, 0.0\}) = \emptyset$$

---

## 3. MoSCoW Requirements

### 3.1 Pillar A: Native Typed Decision Domain Algebra [MUST]
- **`REQ-TYPED-001` [MUST]**: Define native immutable domain types in `br.com.wallet.fraud.decision.api`:
  - `DecisionState`: Immutable snapshot aggregating transaction, behavioral profile, device, network, and graph evidence.
  - `DecisionValue`: Sealed interface permitting `BooleanDecision(boolean)`, `ScoreDecision(double, ScoreCalibration)`, `ChoiceDecision(String, double confidence)`.
  - `DecisionQuestion<T extends DecisionValue>`: Type witness `Class<T>`, `QuestionId`, `RubricVersion`, `Set<EvidenceRequirement>`.
  - `DecisionOutcome<T extends DecisionValue>`: Sealed interface permitting `DecisionAnswer<T>(T answer, Set<EvidenceReference>, DecisionProvenance)` and `DecisionUnavailable<T>(QuestionId, String reason, DecisionProvenance)`.
  - `EvaluatedQuestion<T extends DecisionValue>`: Composite pair `(DecisionQuestion<T> question, DecisionOutcome<T> outcome)`.
  - `EvaluationResult`: Immutable container `List<EvaluatedQuestion<?>>` preserving generic type pairing.
  - `EvidenceReference`: Machine-verifiable record `(EvidenceType type, String evidenceId, String contentHash)`.
  - `DecisionProvenance`: Record `(questionId, snapshotId, snapshotHash, evidenceReferences, evaluatorId, evaluatorVersion, modelId, modelVersion, evaluationTimestamp, rubricVersion)`.
- **`REQ-TYPED-002` [MUST]**: Domain algebra types MUST NOT import or depend on Spring AI, Ollama, ONNX Runtime, or external libraries (`I-TYPED-003`).
- **`REQ-TYPED-005` [MUST]**: Formalize `FraudDecisionQuestions` catalog containing versioned domain definitions (`BEHAVIOR_ANOMALY`, `COORDINATED_TOPOLOGY`, `DEVICE_NETWORK_RISK`) with explicit type witnesses. Modifying `rubricVersion` invalidates prior benchmark baselines and requires a new dataset baseline.
- **`REQ-TYPED-015` [MUST]**: The runtime type witness `Class<T>` MUST agree with the generic parameter; evaluators MUST validate and reject mismatched result types (`MismatchedDecisionTypeException`).

### 3.2 Pillar B: Provider-Neutral SPI & Composition [MUST]
- **`REQ-TYPED-003` [MUST]**: Expose a provider-neutral domain SPI:
  ```java
  public interface DecisionEvaluator {
      EvaluationResult evaluate(DecisionState state, Collection<DecisionQuestion<? extends DecisionValue>> questions);
  }
  ```
- **`REQ-TYPED-004` [MUST]**: Multiple questions MUST be evaluated independently against the same immutable `DecisionState` snapshot without cross-question mutation. Implementations MAY execute questions concurrently or as a provider-native batch.
- **`REQ-TYPED-006` [MUST]**: Implement `DecisionComposer` providing deterministic composition of `EvaluationResult` into `SemanticEvaluation` signals without mutating core ledger state. The composer MUST adhere to `I-TYPED-006` and explicitly handle `DecisionUnavailable` without implicit default coercion.

### 3.3 Pillar C: Architectural Airgap & Nearline Flow Attachment [MUST]
- **`REQ-TYPED-007` [MUST]**: The financial authorization hot path (`POST /operations/*`) MUST NOT invoke `DecisionEvaluator` (`I-TYPED-001`, `I-TYPED-002`). Evaluations are strictly nearline (investigations, analyst checkpoints) or offline benchmarks.
- **`REQ-TYPED-013` [MUST]**: Failure, timeout, or absence of an evaluator MUST return `DecisionUnavailable<T>` without confidence (`I-TYPED-005`) and MUST NOT block outbox processing or Core transactions.
- **`REQ-TYPED-017` [MUST]**: **Post-Fusion Nearline Attachment**: When deterministic fusion (`SPEC-000.8`) outputs `REVIEW` or `RESTRICT`, `InvestigationDispatcher` MUST snapshot facts into `DecisionEvidence` (canonical SHA-256), invoke `DecisionEvaluator` nearline using local SLM (`smollm2`), run `DecisionComposer`, and store structured `CompoundRiskAssessment` in checkpoints. Deterministic fusion math ($R_{\text{final}}$) and direct rules MUST NOT be modified.
- **`REQ-TYPED-018` [MUST]**: **Human-in-the-Loop Analyst API**: `FraudInvestigationController` MUST expose typed endpoints (`GET /fraud/investigations/{entityId}/decisions` and `POST /fraud/investigations/{entityId}/evaluate`) enabling compliance analysts to inspect grounded verdicts and trigger on-demand question evaluation before submitting `AnalystReviewRequest`.

### 3.4 Pillar D: Five-Gate Benchmark Evaluation Protocol [SHOULD / COULD]
- **`REQ-TYPED-009` [SHOULD]**: Five-Gate Evaluation Protocol:
  1. **Gate 1 (Contract Validity)**: 100% structural type safety (`EvaluatedQuestion<T>`), valid outcome states, and verified evidence hashes.
  2. **Gate 2 (Evidence Grounding)**: Direct verification of machine-readable `EvidenceReference` content hashes.
  3. **Gate 3 (Predictive Correctness)**: Task-equivalent Precision/Recall/F1 within pre-declared margin $\epsilon_{\text{workload}}$ along metric optimization direction (`I-TYPED-004`).
  4. **Gate 4 (Calibration)**: Reliability diagrams and Brier score verification strictly for `ScoreDecision`.
  5. **Gate 5 (Operational Performance & Determinism)**: Throughput (evals/sec), $P50/P95/P99$ latency, heap/RSS memory, CPU ceiling, and semantic variance under identical inputs/seeds.
- **`REQ-TYPED-012` [SHOULD]**: Candidate evaluation workloads MUST declare CPU/memory limits adhering to the target appliance profile budget.
- **`REQ-TYPED-014` [COULD]**: Implement experimental `TypeSafeInspiredEvaluator` adapter in test scope for shadow evaluation without production dependencies.

### 3.5 Out of Scope [WON'T]
- **`REQ-TYPED-010` [WON'T]**: Adding `spring-ai-typesafe` or experimental 0.1.0 SNAPSHOT artifacts to production runtime dependencies in V4.
- **`REQ-TYPED-011` [WON'T]**: Permitting semantic decision evaluations to directly execute automated account `HARD_BLOCK` without deterministic rule verification or human-in-the-loop analyst review.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Module | Affected Flow | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`ledger`** | Fund Transfers & Deposits | Latency inflation or hang if evaluator invoked | `I-TYPED-001`: Total airgap; zero evaluator calls on transaction path. |
| **`fraud`** | Hot Risk Gate (`evaluateAuthorization`) | Availability loss on model crash | `I-TYPED-002`: Hot path uses local Dragonfly cache ($P99 < 0.5\text{ms}$). |
| **`fraud`** | Nearline Investigation (`SPEC-000.7`) | Evaluator timeout or crash | `I-TYPED-005`: Bounded fallback to `DecisionUnavailable`; investigation proceeds. |
| **`appliance`**| Appliance Resource Management | Evaluator consumes Core/DB memory | `REQ-TYPED-012`: Evaluator runs within declared appliance profile budget. |

---

## 5. Deterministic Test Triads (`I-TDD-002`)

### Triad 1: Architectural Hot-Path Airgap (`REQ-TYPED-007`)
- **Positive**: Core transfer completes in $<15\text{ms}$ with fraud authorization $<2\text{ms}$; zero invocation of `DecisionEvaluator`.
- **Boundary**: Attempting to inject `DecisionEvaluator` into hot-path `FraudGate` fails architectural boundary test (`ProcessBoundaryArchitectureTest`).

### Triad 2: Strongly Typed Generic Question-Outcome Binding (`REQ-TYPED-001`, `REQ-TYPED-015`)
- **Positive**: Evaluator processes `DecisionQuestion<BooleanDecision>`; returns `EvaluatedQuestion<BooleanDecision>` carrying `DecisionAnswer<BooleanDecision>`.
- **Invalid Input**: Runtime type witness `BooleanDecision.class` passed with `ScoreDecision` payload throws `MismatchedDecisionTypeException`.
- **Boundary**: Attempting to extract mismatched outcome type from `EvaluationResult.outcomeFor(question)` fails compile-time check.

### Triad 3: Unavailable Decision & Absent Confidence (`REQ-TYPED-001`, `REQ-TYPED-013`)
- **Positive**: Evaluator completes within timeout; returns valid `DecisionAnswer<ScoreDecision>` with calibrated score.
- **Boundary**: Evaluator timeout returns `DecisionUnavailable<ScoreDecision>` and the outcome exposes no DecisionAnswer, score, or confidence value. Zero synthetic score or confidence materialized (`I-TYPED-005`).

### Triad 4: Composer Unavailable Semantics & Anti-Coercion Gate (`REQ-TYPED-006`, `I-TYPED-006`)
- **Positive**: $Q_1=\text{true}, Q_2=\text{true}, Q_3=0.82 \to$ deterministic `SemanticEvaluation`.
- **Invalid Input**: Mandatory $Q_2=\text{DecisionUnavailable}$ causes `DecisionComposer` to refuse composition or emit unverified signal according to explicit policy.
- **Boundary**: Unavailable $Q_2$ MUST NOT be coerced to `false`, `0`, or a default synthetic score (`I-TYPED-006`).
