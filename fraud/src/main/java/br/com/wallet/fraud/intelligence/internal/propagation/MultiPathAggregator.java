package br.com.wallet.fraud.intelligence.internal.propagation;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Aggregates independent topological path influences using probabilistic union:
 * R_propagated = 1 - ∏ (1 - I_p).
 */
@Component
public class MultiPathAggregator {

    /**
     * Aggregates multiple path influences into a single propagated risk score.
     */
    public double aggregatePathInfluences(@NonNull List<Double> pathInfluences) {
        Objects.requireNonNull(pathInfluences, "pathInfluences cannot be null");

        if (pathInfluences.isEmpty()) {
            return 0.0;
        }

        double complementProduct = 1.0;

        for (final Double influence : pathInfluences) {
            if (influence == null) {
                continue;
            }
            double clamped = Math.clamp(influence, 0.0, 1.0);
            complementProduct *= (1.0 - clamped);
        }

        double result = 1.0 - complementProduct;
        return Math.clamp(result, 0.0, 1.0);
    }
}
