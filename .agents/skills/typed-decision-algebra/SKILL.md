---
name: typed-decision-algebra
description: Native Java typed decision algebra, generic question-outcome binding, anti-coercion composition, machine-verifiable evidence hashing, and Five-Gate semantic evaluation protocol.
---

# 📐 Native Typed AI Decision Algebra Skill

## 1. Identity & Architectural Mantra

This skill guides the design, implementation, and verification of **native Java 27 typed decision algebra**, **generic question-outcome binding**, **machine-verifiable evidence hashing**, and **candidate semantic evaluation protocols** within the Wallet platform.

> *"Steal the architecture, not the library. Keep the hot transactional path strictly air-gapped from semantic inference. Model decisions with compile-time generic type witnesses, machine-verifiable evidence hashes, and an absolute anti-coercion gate: unavailable decisions can never be coerced into false, zero, or synthetic scores."*

---

## 2. Core Invariants

| Invariant ID | Name | Formal Invariant Rule |
| :--- | :--- | :--- |
| **`I-TYPED-001`** | **Hot-Path Airgap** | $\text{Deps}(\text{HotPath}) \cap \text{Packages}(\text{decision}) = \emptyset$. Core write path and `FraudGate.evaluateAuthorization()` MUST NOT import or invoke semantic decision evaluators. |
| **`I-TYPED-002`** | **Latency Non-Degradation** | $\Delta P99(\text{FraudGate}) = 0\text{ms}$. SLM inference runs exclusively nearline or asynchronously. |
| **`I-TYPED-003`** | **Generic Question-Outcome Binding** | $\forall q \in \text{DecisionQuestion}\langle T \rangle, \quad \text{Outcome}(q) \in \text{DecisionOutcome}\langle T \rangle$ enforced at compile-time and validated via runtime type witness $T.\text{class}$. |
| **`I-TYPED-004`** | **Machine-Verifiable Evidence** | $\forall e \in \text{DecisionEvidence}, \quad e.\text{contentHash} = \text{SHA256}(\text{CanonicalJson}(e.\text{facts}))$. |
| **`I-TYPED-005`** | **Fault & Timeout Containment** | Evaluator timeouts or provider failures MUST produce `DecisionUnavailable<T>` with provenance error reason, never throwing unchecked exceptions or returning synthetic scores. |
| **`I-TYPED-006`** | **Anti-Coercion Composition Gate** | $\forall c \in \text{DecisionComposer}, \quad c(\text{DecisionUnavailable}\langle T \rangle) \ne \text{Coerce}(0, \text{false}, \text{defaultScore})$. Unavailable decisions must not be silently converted to benign or synthetic values. |

---

## 3. Native Algebraic Type System & Generic Binding

### 3.1 Decision Values (`DecisionValue`)
```java
public sealed interface DecisionValue
    permits BooleanDecision, ScoreDecision, CategoryDecision, TextDecision, MultiSelectDecision {
}

public record BooleanDecision(boolean value, String rationale) implements DecisionValue {}
public record ScoreDecision(BigDecimal score, String rationale) implements DecisionValue {
    public ScoreDecision {
        Objects.requireNonNull(score, "score must not be null");
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("score must be in range [0.00, 1.00]");
        }
    }
}
public record CategoryDecision(String category, String rationale) implements DecisionValue {}
public record TextDecision(String summary) implements DecisionValue {}
public record MultiSelectDecision(Set<String> selected, String rationale) implements DecisionValue {}
```

### 3.2 Generic Question with Type Witness
```java
public record DecisionQuestion<T extends DecisionValue>(
    String questionId,
    String questionKey,
    String description,
    Class<T> valueType
) {
    public DecisionQuestion {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(questionKey, "questionKey must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(valueType, "valueType must not be null");
    }
}
```

### 3.3 Algebraic Outcome Sealed Hierarchy
```java
public sealed interface DecisionOutcome<T extends DecisionValue>
    permits DecisionAnswer, DecisionUnavailable {
}

public record DecisionAnswer<T extends DecisionValue>(
    T value,
    Confidence confidence,
    List<DecisionEvidence> grounding,
    DecisionProvenance provenance
) implements DecisionOutcome<T> {
    public DecisionAnswer {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        grounding = List.copyOf(grounding != null ? grounding : List.of());
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
}

public record DecisionUnavailable<T extends DecisionValue>(
    UnavailableReason reason,
    String diagnosticMessage,
    DecisionProvenance provenance
) implements DecisionOutcome<T> {
    public DecisionUnavailable {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(diagnosticMessage, "diagnosticMessage must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
    // CRITICAL: Structurally NO score(), value(), or confidence() accessors!
}
```

### 3.4 Evaluated Question & Strongly Typed Lookup
```java
public record EvaluatedQuestion<T extends DecisionValue>(
    DecisionQuestion<T> question,
    DecisionOutcome<T> outcome
) {
    public EvaluatedQuestion {
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }
}

public record EvaluationResult(
    String evaluationId,
    String subjectId,
    List<EvaluatedQuestion<?>> questions,
    Instant evaluatedAt
) {
    
    public <T extends DecisionValue> DecisionOutcome<T> outcomeFor(DecisionQuestion<T> question) {
        return questions.stream()
            .filter(eq -> eq.question().questionId().equals(question.questionId()))
            .findFirst()
            .map(eq -> {
                // Runtime type witness verification
                if (!eq.question().valueType().equals(question.valueType())) {
                    throw new MismatchedDecisionTypeException(
                        "Expected " + question.valueType() + " but found " + eq.question().valueType());
                }
                return (DecisionOutcome<T>) eq.outcome();
            })
            .orElseThrow(() -> new NoSuchElementException("Question not found: " + question.questionId()));
    }
}
```

---

## 4. Machine-Verifiable Evidence Hashing (`I-TYPED-004`)

Evidence provided to semantic evaluators must be immutable and content-hashed to ensure determinism and auditability:

```java
public record DecisionEvidence(
    String evidenceKey,
    String summary,
    Map<String, Object> facts,
    String contentHash
) {
    public static DecisionEvidence of(String key, String summary, Map<String, Object> facts) {
        String canonicalJson = CanonicalJsonSerializer.serialize(facts);
        String hash = Hashing.sha256Hex(canonicalJson);
        return new DecisionEvidence(key, summary, Map.copyOf(facts), hash);
    }
}
```

### Evidence Canonicalization Rules:
1. Map keys must be sorted lexicographically (tree order).
2. Numbers must be formatted deterministically (no trailing zeros discrepancies).
3. Hash algorithm is standard SHA-256 (`MessageDigest.getInstance("SHA-256")`).

---

## 5. Anti-Coercion Composition Patterns (`I-TYPED-006`)

Downstream composers must never silently coerce `DecisionUnavailable<T>` into falsy or neutral values:

```java
public class DecisionComposer {

    public CompoundRiskAssessment compose(EvaluationResult result, CompositionPolicy policy) {
        List<DecisionUnavailable<?>> unavailables = result.questions().stream()
            .map(EvaluatedQuestion::outcome)
            .filter(outcome -> outcome instanceof DecisionUnavailable)
            .map(outcome -> (DecisionUnavailable<?>) outcome)
            .toList();

        if (!unavailables.isEmpty()) {
            if (policy == CompositionPolicy.FAIL_CLOSED) {
                return CompoundRiskAssessment.inconclusive(
                    "Mandatory evaluations unavailable: " + unavailables.size(), unavailables);
            }
            if (policy == CompositionPolicy.DEGRADE_TO_UNVERIFIED) {
                return CompoundRiskAssessment.unverifiedSignal(unavailables);
            }
        }

        // Process verified answers safely with pattern matching
        
    }
}
```

---

## 6. Provider-Neutral Evaluator SPI & Timeout Handling

```java
public interface DecisionEvaluator {
    CompletableFuture<EvaluationResult> evaluate(
        String subjectId,
        List<DecisionQuestion<?>> questions,
        Map<String, DecisionEvidence> evidence
    );
}
```

### Safe Timeout Handling Pattern:
```java
public CompletableFuture<EvaluationResult> evaluate( ) {
    return client.inferAsync(payload)
        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .handle((response, ex) -> {
            if (ex != null) {
                // Timeout or SLM crash: map all questions to DecisionUnavailable
                return mapToUnavailable(questions, UnavailableReason.TIMEOUT, ex.getMessage());
            }
            return parseResponse(questions, response);
        });
}
```

---

## 7. Five-Gate Semantic Evaluation Protocol

When testing candidate evaluators, prompt versions, or model weights:

1. **Gate 1: Contract Adherence**
   - 100% type matching against `question.valueType()`.
   - Zero malformed JSON or unparseable fields.
2. **Gate 2: Grounding & Provenance**
   - 100% of claims in `grounding` reference valid `contentHash` values present in input evidence.
3. **Gate 3: Task Correctness (Directed Margins)**
   - $\text{F1}_{\text{candidate}} \ge \text{F1}_{\text{baseline}} - \epsilon_{\text{workload}}$ (with pre-declared margin $\epsilon_{\text{workload}} = 0.02$).
4. **Gate 4: Expected Calibration Error (ECE)**
   - $\text{ECE}_{\text{candidate}} \le \text{ECE}_{\text{baseline}} + \epsilon_{\text{calib}}$.
   - Measures reliability of `Confidence` against observed empirical accuracy across probability bins.
5. **Gate 5: Operational Performance & Determinism**
   - Throughput $\ge \text{target}$ evaluations/sec.
   - Latency $P95 \le \text{budget}$.
   - 100% deterministic replay at temperature = 0.

---

## 8. Verification & Test Commands

```bash
# Unit test suite for native decision algebra and anti-coercion gate
./gradlew test --tests "br.com.wallet.fraud.decision.*"

# Modulith architecture boundary tests (assert zero hot-path leakage)
./gradlew test --tests "br.com.wallet.ModulithArchitectureTest"

# Five-Gate benchmark harness execution
./gradlew test --tests "br.com.wallet.fraud.decision.benchmark.*"
```
