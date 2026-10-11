package br.com.wallet.fraud.fusion.internal.fusion;

import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.RiskFusionWeights;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Correlates structural graph topology risk with multi-hop propagated risk (REQ-FUSION-001):
 * R_graph_group = 1 - (1 - R_graph) * (1 - w_p * R_propagated)
 */
public final class GraphIntelligenceCorrelationGroup implements RiskCorrelationGroup {

    public static final String NAME = "GRAPH_INTELLIGENCE";

    @Override
    @NonNull
    public String name() {
        return NAME;
    }

    @Override
    public double calculateGroupRisk(@NonNull FraudSignalSet signals, @NonNull RiskFusionWeights weights) {
        Objects.requireNonNull(signals, "signals cannot be null");
        Objects.requireNonNull(weights, "weights cannot be null");

        double rGraph = signals.graphRisk();
        double rProp = signals.propagatedRisk();
        double wp = weights.propagatedWeight();

        double groupRisk = 1.0 - (1.0 - rGraph) * (1.0 - wp * rProp);
        return Math.clamp(groupRisk, 0.0, 1.0);
    }
}
