package br.com.wallet.fraud.intelligence.propagation;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event published when an entity's propagated risk exceeds the configured alert threshold.
 */
public record EntityRiskPropagationDetectedEvent(
    @NonNull UUID entityId,
    double propagatedRisk,
    int hopCount,
    @NonNull String strongestRelationship,
    @NonNull UUID rootSourceId,
    @NonNull Instant timestamp
) {
    public EntityRiskPropagationDetectedEvent {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(strongestRelationship, "strongestRelationship cannot be null");
        Objects.requireNonNull(rootSourceId, "rootSourceId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }
}
