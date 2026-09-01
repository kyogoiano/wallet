package br.com.wallet.fraud.intelligence.domain;

public record GraphRiskSignals(
    double cycleRisk,
    double fanInRisk,
    double fanOutRisk,
    double sharedIdentityRisk,
    double muleHubRisk
) {
    public static GraphRiskSignals zero() {
        return new GraphRiskSignals(0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public double calculateCompositeScore() {
        double weighted = (0.35 * clamp(cycleRisk))
                        + (0.25 * clamp(muleHubRisk))
                        + (0.20 * clamp(sharedIdentityRisk))
                        + (0.10 * clamp(fanInRisk))
                        + (0.10 * clamp(fanOutRisk));
        return Math.clamp(weighted, 0.0, 1.0);
    }

    private static double clamp(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }
}
