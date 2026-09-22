package br.com.wallet.fraud.decision.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit trail and provenance metadata for an evaluated decision.
 *
 * @param modelIdentifier identifier of the model or algorithm (e.g. qwen2.5:7b-instruct-q4_K_M)
 * @param promptVersion version tag of the prompt template or rule set
 * @param timestamp execution instant
 * @param latencyMs evaluation duration in milliseconds
 */
public record DecisionProvenance(
    String modelIdentifier,
    String promptVersion,
    Instant timestamp,
    Long latencyMs
) {

    public DecisionProvenance {
        Objects.requireNonNull(modelIdentifier, "modelIdentifier must not be null");
        Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(latencyMs, "latencyMs must not be null");
    }
}
