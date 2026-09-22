package br.com.wallet.fraud.decision.model;

import java.util.Objects;

/**
 * Generic question specification with compile-time type binding and runtime type-witness.
 *
 * @param <T> concrete decision value type extending {@link DecisionValue}
 * @param questionId unique identifier for the question instance
 * @param questionKey semantic catalog key (e.g., BEHAVIOR_ANOMALY)
 * @param description human-readable question prompt
 * @param valueType runtime type witness class for safe dynamic casting and schema matching
 */
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
