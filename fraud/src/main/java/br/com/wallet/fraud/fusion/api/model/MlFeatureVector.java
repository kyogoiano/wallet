package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Versioned tabular feature tensor for embedded Micro-ML inference (I-FUSION-008).
 */
public record MlFeatureVector(
    int featureVersion,
    @NonNull List<String> featureNames,
    float[] values
) {
    public MlFeatureVector {
        Objects.requireNonNull(featureNames, "featureNames cannot be null");
        Objects.requireNonNull(values, "values cannot be null");
        featureNames = List.copyOf(featureNames);
    }
}
