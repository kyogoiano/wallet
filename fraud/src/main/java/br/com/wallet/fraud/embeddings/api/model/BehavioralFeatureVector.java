package br.com.wallet.fraud.embeddings.api.model;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Dual representation of an entity's 16-D behavioral profile:
 * - L2-normalized unit vector (||v||_2 = 1.0) representing behavioral pattern shape/direction.
 * - Scalar feature magnitude (||d||_2) representing behavioral intensity.
 */
public record BehavioralFeatureVector(
    @NonNull UUID entityId,
    double[] vector,
    double featureMagnitude,
    long transactionCount,
    @NonNull BigDecimal transactionVolume
) {
    public BehavioralFeatureVector {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(vector, "vector cannot be null");
        Objects.requireNonNull(transactionVolume, "transactionVolume cannot be null");
        if (vector.length != 16) {
            throw new IllegalArgumentException("Behavioral vector must have exactly 16 dimensions, got: " + vector.length);
        }
        vector = Arrays.copyOf(vector, vector.length);
    }

    @Override
    public double[] vector() {
        return Arrays.copyOf(vector, vector.length);
    }

    /**
     * Creates an inactive profile with zero vector, zero magnitude, and zero volume per I-VEC-008.
     */
    public static BehavioralFeatureVector inactive(@NonNull final UUID entityId) {
        return new BehavioralFeatureVector(
            entityId,
            new double[16],
            0.0,
            0L,
            BigDecimal.ZERO
        );
    }
}
