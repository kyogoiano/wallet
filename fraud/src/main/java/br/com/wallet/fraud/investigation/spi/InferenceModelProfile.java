package br.com.wallet.fraud.investigation.spi;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Objects;

public record InferenceModelProfile(
    @NonNull String modelName,
    int maxTokens,
    double temperature,
    @NonNull Duration timeout
) {
    public InferenceModelProfile {
        Objects.requireNonNull(modelName, "modelName cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
    }

    public static InferenceModelProfile fast() {
        return new InferenceModelProfile("llama3.2:1b", 256, 0.0, Duration.ofMillis(500));
    }

    public static InferenceModelProfile balanced() {
        return new InferenceModelProfile("llama3.2:3b", 512, 0.0, Duration.ofSeconds(2));
    }

    public static InferenceModelProfile highQuality() {
        return new InferenceModelProfile("llama3.1:8b", 1024, 0.0, Duration.ofSeconds(5));
    }
}
