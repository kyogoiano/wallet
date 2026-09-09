package br.com.wallet.fraud.fusion.internal.fusion;

import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.RiskFusionWeights;
import org.jspecify.annotations.NonNull;

/**
 * Extensible contract for correlating non-independent risk signals before master probabilistic fusion.
 */
public interface RiskCorrelationGroup {

    @NonNull
    String name();

    double calculateGroupRisk(@NonNull FraudSignalSet signals, @NonNull RiskFusionWeights weights);
}
