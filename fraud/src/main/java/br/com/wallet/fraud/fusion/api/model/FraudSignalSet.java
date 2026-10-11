package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Multi-signal input value object containing raw risk scores across direct rules,
 * graph intelligence, risk propagation, behavioral anomaly, and micro-ML.
 */
public record FraudSignalSet(
    double directRisk,
    double graphRisk,
    double propagatedRisk,
    double behavioralRisk,
    @NonNull MlRiskResult mlRisk
) {
    public FraudSignalSet {
        validateScore("directRisk", directRisk);
        validateScore("graphRisk", graphRisk);
        validateScore("propagatedRisk", propagatedRisk);
        validateScore("behavioralRisk", behavioralRisk);
        Objects.requireNonNull(mlRisk, "mlRisk cannot be null");
    }

    private static void validateScore(String name, double score) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0], was: " + score);
        }
    }

    public static FraudSignalSet of(
        double directRisk,
        double graphRisk,
        double propagatedRisk,
        double behavioralRisk,
        double mlScore
    ) {
        return new FraudSignalSet(
            directRisk,
            graphRisk,
            propagatedRisk,
            behavioralRisk,
            new MlRiskResult.Available(mlScore, "v1", 0L)
        );
    }

    public static FraudSignalSet withoutMl(
        double directRisk,
        double graphRisk,
        double propagatedRisk,
        double behavioralRisk,
        @NonNull String reason
    ) {
        return new FraudSignalSet(
            directRisk,
            graphRisk,
            propagatedRisk,
            behavioralRisk,
            new MlRiskResult.Unavailable(reason)
        );
    }
}
