package br.com.wallet.fraud.fusion.api.model;

/**
 * Sensitivity weights for risk fusion and correlation groups.
 */
public record RiskFusionWeights(
    double graphWeight,
    double propagatedWeight,
    double behavioralWeight,
    double mlWeight
) {
    public RiskFusionWeights {
        validateWeight("graphWeight", graphWeight);
        validateWeight("propagatedWeight", propagatedWeight);
        validateWeight("behavioralWeight", behavioralWeight);
        validateWeight("mlWeight", mlWeight);
    }

    private static void validateWeight(String name, double weight) {
        if (weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0], was: " + weight);
        }
    }

    public static RiskFusionWeights defaults() {
        return new RiskFusionWeights(0.85, 0.75, 0.70, 0.60);
    }
}
