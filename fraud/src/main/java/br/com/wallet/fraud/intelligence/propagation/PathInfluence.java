package br.com.wallet.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates the calculated influence of a single directed topological path.
 */
public record PathInfluence(
    @NonNull UUID sourceEntityId,
    @NonNull UUID targetEntityId,
    int hopCount,
    @NonNull List<UUID> nodes,
    @NonNull List<RelationshipType> relationshipTypes,
    @NonNull List<Instant> eventTimestamps,
    double rawPathInfluence,
    @NonNull RelationshipType strongestRelationship
) {
    public PathInfluence {
        Objects.requireNonNull(sourceEntityId, "sourceEntityId cannot be null");
        Objects.requireNonNull(targetEntityId, "targetEntityId cannot be null");
        Objects.requireNonNull(nodes, "nodes cannot be null");
        Objects.requireNonNull(relationshipTypes, "relationshipTypes cannot be null");
        Objects.requireNonNull(eventTimestamps, "eventTimestamps cannot be null");
        Objects.requireNonNull(strongestRelationship, "strongestRelationship cannot be null");
        nodes = Collections.unmodifiableList(nodes);
        relationshipTypes = Collections.unmodifiableList(relationshipTypes);
        eventTimestamps = Collections.unmodifiableList(eventTimestamps);
    }
}
