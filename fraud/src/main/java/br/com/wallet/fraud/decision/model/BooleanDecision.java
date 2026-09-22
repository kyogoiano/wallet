package br.com.wallet.fraud.decision.model;

import java.util.Objects;

/**
 * Binary boolean decision with domain rationale.
 *
 * @param value boolean verdict
 * @param rationale human-readable or audit explanation
 */
public record BooleanDecision(
    boolean value,
    String rationale
) implements DecisionValue {

    public BooleanDecision {
        Objects.requireNonNull(rationale, "rationale must not be null");
    }
}
