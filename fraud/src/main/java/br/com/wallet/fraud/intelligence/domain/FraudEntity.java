package br.com.wallet.fraud.intelligence.domain;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record FraudEntity(
    @NonNull UUID id,
    @NonNull EntityType entityType,
    double directRisk,
    double graphRisk,
    double behavioralRisk,
    double propagatedRisk,
    double finalRisk,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt,
    @Nullable Map<String, Object> metadata
) {
    public FraudEntity {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    public static FraudEntity create(@NonNull UUID id, @NonNull EntityType entityType, @NonNull Instant now) {
        return new FraudEntity(id, entityType, 0.0, 0.0, 0.0, 0.0, 0.0, now, now, Collections.emptyMap());
    }

    public FraudEntity withGraphRisk(double newGraphRisk, @NonNull Instant now) {
        return new FraudEntity(
            this.id,
            this.entityType,
            this.directRisk,
            newGraphRisk,
            this.behavioralRisk,
            this.propagatedRisk,
            this.finalRisk,
            this.createdAt,
            Objects.requireNonNull(now, "now cannot be null"),
            this.metadata
        );
    }
}
