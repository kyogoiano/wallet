package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * The final output of the multi-signal risk fusion calculation, containing the bounded final score,
 * the 4-state fraud decision, original signals, and full marginal attribution.
 */
public record RiskFusionResult(
    double finalRisk,
    @NonNull FraudDecision decision,
    @NonNull FraudSignalSet signals,
    @NonNull RiskAttribution attribution
) {
    public RiskFusionResult {
        Objects.requireNonNull(decision, "decision cannot be null");
        Objects.requireNonNull(signals, "signals cannot be null");
        Objects.requireNonNull(attribution, "attribution cannot be null");
    }

    public static RiskFusionResult hardBlocked(@NonNull FraudSignalSet signals) {
        Objects.requireNonNull(signals, "signals cannot be null");
        List<FactorContribution> contributions = List.of(
            new FactorContribution("DIRECT_RULE", signals.directRisk(), 1.0, 100.0)
        );
        RiskAttribution attribution = new RiskAttribution("DIRECT_HARD_RULE", contributions);
        return new RiskFusionResult(1.0, FraudDecision.HARD_BLOCK, signals, attribution);
    }
}
