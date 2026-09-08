package br.com.wallet.fraud.investigation.spi;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Benchmark candidate model configuration for comparative evaluation (REQ-VEC-007, History 29).
 */
public record ModelCandidate(
    @NonNull String id,
    @NonNull String backend,
    @NonNull InferenceCapability capability
) {
    public ModelCandidate {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(backend, "backend cannot be null");
        Objects.requireNonNull(capability, "capability cannot be null");
    }

    public static ModelCandidate smollm135m() {
        return new ModelCandidate("smollm2:135m", "ollama", InferenceCapability.FAST);
    }

    public static ModelCandidate smollm360m() {
        return new ModelCandidate("smollm2:360m-instruct-q5_K_M", "ollama", InferenceCapability.BALANCED);
    }

    public static ModelCandidate llama1b() {
        return new ModelCandidate("llama3.2:1b", "ollama", InferenceCapability.HIGH_QUALITY);
    }

    public static ModelCandidate smollm17b() {
        return new ModelCandidate("smollm2:1.7b", "ollama", InferenceCapability.HIGH_QUALITY);
    }
}
