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
    @NonNull FraudArchetype topArchetype,
    double archetypeSimilarity
) {
    public FraudRiskSnapshot {
        Objects.requireNonNull(topArchetype, "topArchetype cannot be null");
    }

    public FraudRiskSnapshot(
        double directRisk,
        double graphRisk,
        double propagatedRisk,
        double behavioralRisk,
        double featureMagnitude,
        @NonNull String topArchetypeStr,
        double archetypeSimilarity
    ) {
        this(
            directRisk,
            graphRisk,
            propagatedRisk,
            behavioralRisk,
            featureMagnitude,
            FraudArchetype.fromString(topArchetypeStr),
            archetypeSimilarity
        );
    }

    public static FraudRiskSnapshot empty() {
        return new FraudRiskSnapshot(0.0, 0.0, 0.0, 0.0, 0.0, FraudArchetype.NONE, 0.0);
    }
}
