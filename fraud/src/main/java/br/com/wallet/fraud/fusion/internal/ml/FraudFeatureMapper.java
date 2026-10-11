package br.com.wallet.fraud.fusion.internal.ml;

import br.com.wallet.fraud.fusion.api.model.MlFeatureVector;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Normalizes multi-source domain indicators into a versioned tabular ML feature tensor (I-FUSION-008).
 */
@Component
public final class FraudFeatureMapper {

    public static final int FEATURE_VERSION_V1 = 1;

    public static final List<String> CANONICAL_FEATURE_NAMES_V1 = List.of(
        "direct_risk",
        "graph_risk",
        "propagated_risk",
        "behavioral_risk",
        "tx_amount_normalized",
        "tx_velocity_1h"
    );

    @NonNull
    public MlFeatureVector mapToVector(
        double directRisk,
        double graphRisk,
        double propagatedRisk,
        double behavioralRisk,
        double amount,
        double velocity1h
    ) {
        float[] values = new float[]{
            (float) directRisk,
            (float) graphRisk,
            (float) propagatedRisk,
            (float) behavioralRisk,
            (float) amount,
            (float) velocity1h
        };
        return new MlFeatureVector(FEATURE_VERSION_V1, CANONICAL_FEATURE_NAMES_V1, values);
    }
}
