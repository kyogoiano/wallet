package br.com.wallet.fraud.intelligence.domain;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record FraudRelationshipEvent(
    @NonNull UUID id,
    @NonNull UUID sourceId,
    @NonNull UUID targetId,
    @NonNull RelationshipType relationshipType,
    @NonNull Instant occurredAt,
    @Nullable UUID operationId,
    @Nullable BigDecimal amount,
    @Nullable Map<String, Object> metadata
) {
    public FraudRelationshipEvent {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(sourceId, "sourceId cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");
        Objects.requireNonNull(relationshipType, "relationshipType cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }
}
