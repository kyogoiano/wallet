package br.com.wallet.fraud.decision.model;

import java.util.List;
import java.util.Objects;

/**
 * Successful semantic decision answer carrying validated value, calibrated confidence,
 * machine-verifiable grounding evidence, and execution provenance.
 *
 * @param <T> concrete decision value type extending {@link DecisionValue}
 * @param value validated decision payload
 * @param confidence calibrated confidence assessment
 * @param grounding machine-verifiable evidence supporting this answer
 * @param provenance audit trail of model, prompt, and execution latency
 */
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
