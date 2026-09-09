package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Model artifact manifest containing version, schema, and checksum metadata (I-FUSION-008).
 */
public record OnnxModelMetadata(
    @NonNull String modelId,
    @NonNull String modelVersion,
    int featureVersion,
    @NonNull List<String> featureNames,
    @Nullable String checksum
) {
    public OnnxModelMetadata {
        Objects.requireNonNull(modelId, "modelId cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(featureNames, "featureNames cannot be null");
        featureNames = List.copyOf(featureNames);
    }
}
