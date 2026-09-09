package br.com.wallet.fraud.fusion.api;

import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.api.model.RiskFusionWeights;
import org.jspecify.annotations.NonNull;

/**
 * Public mathematical contract for fusing multi-signal fraud indicators into a unified risk score.
 */
public interface RiskFusionEngine {

    @NonNull
    RiskFusionResult fuse(@NonNull FraudSignalSet signals, @NonNull RiskFusionWeights weights);
}
