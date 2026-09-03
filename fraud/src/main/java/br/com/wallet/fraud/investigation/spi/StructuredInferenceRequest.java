package br.com.wallet.fraud.investigation.spi;

import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record StructuredInferenceRequest(
    @NonNull SanitizedInferenceContext context,
    @NonNull InferenceCapability capability,
    @NonNull InferenceModelProfile profile,
    @NonNull String systemPrompt,
    @NonNull String jsonSchemaConstraint
) {
    public StructuredInferenceRequest {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(capability, "capability cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(systemPrompt, "systemPrompt cannot be null");
        Objects.requireNonNull(jsonSchemaConstraint, "jsonSchemaConstraint cannot be null");
    }
}
