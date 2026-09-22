package br.com.wallet.fraud.decision.model;

import java.util.Objects;

/**
 * Categorical semantic classification outcome.
 *
 * @param category pre-declared classification label
 * @param rationale classification justification
 */
public record CategoryDecision(
    String category,
    String rationale
) implements DecisionValue {

    public CategoryDecision {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
    }
}
