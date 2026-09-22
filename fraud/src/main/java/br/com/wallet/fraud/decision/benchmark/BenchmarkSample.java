package br.com.wallet.fraud.decision.benchmark;

import br.com.wallet.fraud.decision.model.DecisionEvidence;
import br.com.wallet.fraud.decision.model.DecisionQuestion;
import br.com.wallet.fraud.decision.model.DecisionValue;

import java.util.Map;
import java.util.Objects;

/**
 * A benchmark evaluation test case with ground-truth expected outcome.
 */
public record BenchmarkSample(
    String subjectId,
    DecisionQuestion<?> question,
    Map<String, DecisionEvidence> evidence,
    DecisionValue expectedValue
) {

    public BenchmarkSample {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(question, "question must not be null");
        evidence = Map.copyOf(evidence != null ? evidence : Map.of());
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");
    }
}
