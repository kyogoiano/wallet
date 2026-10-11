package br.com.wallet.fraud.fusion.internal.fusion;

import br.com.wallet.fraud.fusion.api.RiskFusionEngine;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.api.model.RiskAttribution;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.api.model.RiskFusionWeights;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Pure mathematical implementation of the Multi-Signal Probabilistic Risk Fusion Engine (REQ-FUSION-001).
 * Features direct rule primacy override (I-FUSION-001), monotonic correlation groups (I-FUSION-002),
 * Leave-One-Out marginal attribution (I-FUSION-003), and observable ML degradation (I-FUSION-010).
 */
@Component
public final class ProbabilisticRiskFusionEngine implements RiskFusionEngine {

    private final RiskCorrelationGroup graphGroup;
    private final RiskAttributionCalculator attributionCalculator;

    public ProbabilisticRiskFusionEngine() {
        this(new GraphIntelligenceCorrelationGroup(), new RiskAttributionCalculator());
    }

    public ProbabilisticRiskFusionEngine(
        @NonNull RiskCorrelationGroup graphGroup,
        @NonNull RiskAttributionCalculator attributionCalculator
    ) {
        this.graphGroup = Objects.requireNonNull(graphGroup, "graphGroup cannot be null");
        this.attributionCalculator = Objects.requireNonNull(attributionCalculator, "attributionCalculator cannot be null");
    }

    @Override
    @NonNull
    public RiskFusionResult fuse(@NonNull FraudSignalSet signals, @NonNull RiskFusionWeights weights) {
        Objects.requireNonNull(signals, "signals cannot be null");
        Objects.requireNonNull(weights, "weights cannot be null");

        // I-FUSION-001: Direct Rule Primacy Override before math
        if (signals.directRisk() >= 1.0) {
            return RiskFusionResult.hardBlocked(signals);
        }

        // Step 1: Sub-group correlation aggregation (Graph Topology + Propagation)
        double rGg = graphGroup.calculateGroupRisk(signals, weights);

        // Step 2: Master Probabilistic Fusion
        double rDir = signals.directRisk();
        double rBeh = signals.behavioralRisk();
        double wg = weights.graphWeight();
        double wb = weights.behavioralWeight();
        double wm = weights.mlWeight();

        double complement = (1.0 - rDir) * (1.0 - wg * rGg) * (1.0 - wb * rBeh);

        // I-FUSION-010: Degrades gracefully without synthetic proxies when ML is unavailable
        if (signals.mlRisk() instanceof MlRiskResult.Available available) {
            complement *= (1.0 - wm * available.score());
        }

        double finalRisk = Math.clamp(1.0 - complement, 0.0, 1.0);

        // Step 3: Graduated Decision Policy (REQ-FUSION-011)
        FraudDecision decision;
        if (finalRisk >= 0.85) {
            decision = FraudDecision.RESTRICT;
        } else if (finalRisk >= 0.50) {
            decision = FraudDecision.REVIEW;
        } else {
            decision = FraudDecision.ALLOW;
        }

        // Step 4: Leave-One-Out Marginal Attribution (REQ-FUSION-010, I-FUSION-003)
        RiskAttribution attribution = attributionCalculator.calculate(signals, weights, finalRisk, rGg);

        return new RiskFusionResult(finalRisk, decision, signals, attribution);
    }
}
