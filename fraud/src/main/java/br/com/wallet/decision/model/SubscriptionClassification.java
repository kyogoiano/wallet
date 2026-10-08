package br.com.wallet.decision.model;

import java.util.Objects;

/**
 * Semantic subscription category classification value (REQ-SUB-010).
 */
public record SubscriptionClassification(
        String category,
        String rationale
) implements DecisionValue {
    public SubscriptionClassification {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
    }
}
