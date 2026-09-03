package br.com.wallet.fraud.investigation.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Snapshot of all multi-dimensional risk scores and behavioral metrics for an entity.
 */
public record FraudRiskSnapshot(
    double directRisk,
    double graphRisk,
    double propagatedRisk,
    double behavioralRisk,
    double featureMagnitude,
    @NonNull String topArchetype,
    double archetypeSimilarity
) {
    public FraudRiskSnapshot {
        Objects.requireNonNull(topArchetype, "topArchetype cannot be null");
    }

    public static FraudRiskSnapshot empty() {
        return new FraudRiskSnapshot(0.0, 0.0, 0.0, 0.0, 0.0, "NONE", 0.0);
    }
}
