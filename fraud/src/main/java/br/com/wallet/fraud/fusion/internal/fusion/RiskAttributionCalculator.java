package br.com.wallet.fraud.fusion.internal.fusion;

import br.com.wallet.fraud.fusion.api.model.FactorContribution;
import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.api.model.RiskAttribution;
import br.com.wallet.fraud.fusion.api.model.RiskFusionWeights;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes Leave-One-Out (LOO) marginal risk attribution and normalized percentages (REQ-FUSION-010, I-FUSION-003).
 */
public final class RiskAttributionCalculator {

    public static final String DIRECT_RULE = "DIRECT_RULE";
    public static final String GRAPH_INTELLIGENCE = "GRAPH_INTELLIGENCE";
    public static final String BEHAVIORAL_ANOMALY = "BEHAVIORAL_ANOMALY";
    public static final String MICRO_ML = "MICRO_ML";

    public RiskAttribution calculate(
        @NonNull FraudSignalSet signals,
        @NonNull RiskFusionWeights weights,
        double finalRisk,
        double graphGroupRisk
    ) {
        Objects.requireNonNull(signals, "signals cannot be null");
        Objects.requireNonNull(weights, "weights cannot be null");

        double rDir = signals.directRisk();
        double rGg = graphGroupRisk;
        double rBeh = signals.behavioralRisk();
        double wg = weights.graphWeight();
        double wb = weights.behavioralWeight();
        double wm = weights.mlWeight();

        boolean mlAvailable = signals.mlRisk() instanceof MlRiskResult.Available;
        double rMl = mlAvailable ? ((MlRiskResult.Available) signals.mlRisk()).score() : 0.0;
        Optional<String> degradedReason = mlAvailable
            ? Optional.empty()
            : Optional.of(((MlRiskResult.Unavailable) signals.mlRisk()).reason());

        List<RawFactor> rawFactors = new ArrayList<>(4);

        // 1. Direct Rule
        double rWithoutDir = evaluateMaster(0.0, rGg, rBeh, rMl, wg, wb, wm, mlAvailable);
        double cDir = Math.max(0.0, finalRisk - rWithoutDir);
        rawFactors.add(new RawFactor(DIRECT_RULE, rDir, cDir));

        // 2. Graph Intelligence Group
        double rWithoutGg = evaluateMaster(rDir, 0.0, rBeh, rMl, wg, wb, wm, mlAvailable);
        double cGg = Math.max(0.0, finalRisk - rWithoutGg);
        rawFactors.add(new RawFactor(GRAPH_INTELLIGENCE, rGg, cGg));

        // 3. Behavioral Anomaly
        double rWithoutBeh = evaluateMaster(rDir, rGg, 0.0, rMl, wg, wb, wm, mlAvailable);
        double cBeh = Math.max(0.0, finalRisk - rWithoutBeh);
        rawFactors.add(new RawFactor(BEHAVIORAL_ANOMALY, rBeh, cBeh));

        // 4. Micro-ML (only if available)
        if (mlAvailable) {
            double rWithoutMl = evaluateMaster(rDir, rGg, rBeh, 0.0, wg, wb, wm, true);
            double cMl = Math.max(0.0, finalRisk - rWithoutMl);
            rawFactors.add(new RawFactor(MICRO_ML, rMl, cMl));
        }

        double sumMarginal = rawFactors.stream().mapToDouble(RawFactor::marginalImpact).sum();

        List<FactorContribution> contributions = new ArrayList<>(rawFactors.size());
        for (RawFactor factor : rawFactors) {
            double percentage = sumMarginal > 0.0 ? (factor.marginalImpact() / sumMarginal) * 100.0 : 0.0;
            contributions.add(new FactorContribution(
                factor.factorName(),
                factor.rawScore(),
                factor.marginalImpact(),
                percentage
            ));
        }

        String primaryDriver = sumMarginal > 0.0
            ? contributions.stream()
                .max(Comparator.comparingDouble(FactorContribution::normalizedPercentage))
                .map(FactorContribution::factorName)
                .orElse("NONE")
            : "NONE";

        return new RiskAttribution(primaryDriver, contributions, degradedReason);
    }

    private static double evaluateMaster(
        double rDir,
        double rGg,
        double rBeh,
        double rMl,
        double wg,
        double wb,
        double wm,
        boolean mlAvailable
    ) {
        double complement = (1.0 - rDir) * (1.0 - wg * rGg) * (1.0 - wb * rBeh);
        if (mlAvailable) {
            complement *= (1.0 - wm * rMl);
        }
        return Math.clamp(1.0 - complement, 0.0, 1.0);
    }

    private record RawFactor(String factorName, double rawScore, double marginalImpact) {}
}
