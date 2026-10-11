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
        return new InferenceModelProfile("smollm2:135m", 384, 0.0, Duration.ofSeconds(1));
    }

    public static InferenceModelProfile balanced() {
        return new InferenceModelProfile("smollm2:360m-instruct-q5_K_M", 512, 0.0, Duration.ofSeconds(3));
    }

    public static InferenceModelProfile highQuality() {
        return new InferenceModelProfile("llama3.2:1b", 768, 0.0, Duration.ofSeconds(5));
    }
}
