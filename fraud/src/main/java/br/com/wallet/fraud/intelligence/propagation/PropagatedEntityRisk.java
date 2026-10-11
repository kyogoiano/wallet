package br.com.wallet.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the aggregated propagated risk result for a target entity.
 */
public record PropagatedEntityRisk(
    @NonNull UUID entityId,
    double propagatedRisk,
    int shortestHopCount,
    @NonNull RelationshipType primaryRelationship,
    @NonNull Instant evaluatedAt,
    @NonNull String modelVersion
) {
    public PropagatedEntityRisk {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(primaryRelationship, "primaryRelationship cannot be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
    }
}
