package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Observable ML inference result contract (I-FUSION-010).
 * Prevents silent fallbacks to uncalibrated heuristic scores.
 */
public sealed interface MlRiskResult {

    record Available(
        double score,
        @NonNull String modelVersion,
        long inferenceNanos
    ) implements MlRiskResult {
        public Available {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("ML risk score must be in [0.0, 1.0], was: " + score);
            }
            Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
            if (inferenceNanos < 0) {
                throw new IllegalArgumentException("inferenceNanos cannot be negative, was: " + inferenceNanos);
            }
        }
    }

    record Unavailable(
        @NonNull String reason
    ) implements MlRiskResult {
        public Unavailable {
            Objects.requireNonNull(reason, "reason cannot be null");
        }
    }
}
