package br.com.wallet.fraud.embeddings.internal.normalization;

import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Normalizes 16-D raw feature arrays into unit vectors (||v||_2 = 1.0) while preserving
 * scalar magnitude M = ||d||_2 and enforcing Zero-Activity Neutrality (I-VEC-008).
 */
@Component
public class FeatureNormalizer {

    @NonNull
    public BehavioralFeatureVector normalize(
        @NonNull final UUID entityId,
        final double @NonNull [] rawFeatures,
        final long transactionCount,
        @NonNull final BigDecimal transactionVolume
    ) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(rawFeatures, "rawFeatures cannot be null");
        Objects.requireNonNull(transactionVolume, "transactionVolume cannot be null");
        if (rawFeatures.length != 16) {
            throw new IllegalArgumentException("Expected 16 raw features, got: " + rawFeatures.length);
        }

        double sumSquares = 0.0;
        for (double val : rawFeatures) {
            sumSquares += val * val;
        }
        double magnitude = Math.sqrt(sumSquares);

        // I-VEC-008: Zero Activity Neutrality
        if (magnitude <= 0.0 || Double.isNaN(magnitude)) {
            return BehavioralFeatureVector.inactive(entityId);
        }

        double[] normalized = new double[16];
        for (int i = 0; i < 16; i++) {
            normalized[i] = rawFeatures[i] / magnitude;
        }

        return new BehavioralFeatureVector(
            entityId,
            normalized,
            magnitude,
            transactionCount,
            transactionVolume
        );
    }
}
