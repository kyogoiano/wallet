package br.com.wallet.fraud.intelligence.domain;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record FraudRelationship(
    @NonNull UUID sourceId,
    @NonNull UUID targetId,
    @NonNull RelationshipType relationshipType,
    @NonNull Instant firstSeenAt,
    @NonNull Instant lastSeenAt,
    long txCount,
    @NonNull BigDecimal totalAmount,
    @Nullable Map<String, Object> metadata
) {
    public FraudRelationship {
        Objects.requireNonNull(sourceId, "sourceId cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");
        Objects.requireNonNull(relationshipType, "relationshipType cannot be null");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt cannot be null");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt cannot be null");
        Objects.requireNonNull(totalAmount, "totalAmount cannot be null");
        metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }
}
