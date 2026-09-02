package br.com.wallet.fraud.intelligence.internal.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.propagation.PropagationConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Calculates edge decay and multi-hop path influences using exponential half-life decay.
 */
@Component
public class PathInfluenceCalculator {

    private static final double LN_2 = Math.log(2.0);

    /**
     * Calculates the exponential decay factor for a given elapsed duration and half-life:
     * D = e^(-λ * elapsed), where λ = ln(2) / halfLife.
     */
    public double calculateDecayFactor(@NonNull Duration elapsed, @NonNull Duration halfLife) {
        Objects.requireNonNull(elapsed, "elapsed cannot be null");
        Objects.requireNonNull(halfLife, "halfLife cannot be null");

        if (elapsed.isNegative() || elapsed.isZero()) {
            return 1.0;
        }

        double elapsedSeconds = elapsed.toSeconds();
        double halfLifeSeconds = Math.max(1.0, halfLife.toSeconds());

        double lambda = LN_2 / halfLifeSeconds;
        double exponent = -lambda * elapsedSeconds;

        return Math.clamp(Math.exp(exponent), 0.0, 1.0);
    }

    /**
     * Calculates decay for an edge event timestamp relative to a deterministic evaluation timestamp (asOf).
     */
    public double calculateEdgeDecay(@NonNull Instant eventTimestamp, @NonNull Instant asOf, @NonNull Duration halfLife) {
        Objects.requireNonNull(eventTimestamp, "eventTimestamp cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(halfLife, "halfLife cannot be null");

        if (eventTimestamp.isAfter(asOf)) {
            return 1.0;
        }

        Duration elapsed = Duration.between(eventTimestamp, asOf);
        return calculateDecayFactor(elapsed, halfLife);
    }

    /**
     * Computes the compound path influence for a multi-hop traversal:
     * I(p, t) = R_source * ∏ (w(e_i) * e^(-λ Δt_i)).
     */
    public double calculatePathInfluence(
        double sourceRisk,
        @NonNull List<RelationshipType> edgeTypes,
        @NonNull List<Instant> eventTimestamps,
        @NonNull Instant asOf,
        @NonNull PropagationConfig config
    ) {
        Objects.requireNonNull(edgeTypes, "edgeTypes cannot be null");
        Objects.requireNonNull(eventTimestamps, "eventTimestamps cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        if (edgeTypes.isEmpty() || edgeTypes.size() != eventTimestamps.size()) {
            return 0.0;
        }

        double clampedSourceRisk = Math.clamp(sourceRisk, 0.0, 1.0);
        if (clampedSourceRisk <= 0.0) {
            return 0.0;
        }

        double influence = clampedSourceRisk;

        for (int i = 0; i < edgeTypes.size(); i++) {
            RelationshipType type = edgeTypes.get(i);
            Instant eventTime = eventTimestamps.get(i);

            double weight = config.getWeight(type);
            double decay = calculateEdgeDecay(eventTime, asOf, config.halfLife());

            influence *= (weight * decay);

            if (influence <= 0.000001) {
                return 0.0; // Early exit for vanishing influence
            }
        }

        return Math.clamp(influence, 0.0, 1.0);
    }
}
