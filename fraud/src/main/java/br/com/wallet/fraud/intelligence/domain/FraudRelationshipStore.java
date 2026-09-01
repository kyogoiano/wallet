package br.com.wallet.fraud.intelligence.domain;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FraudRelationshipStore {
    void upsertEntity(@NonNull FraudEntity entity);
    @NonNull Optional<FraudEntity> findEntityById(@NonNull UUID id);
    void updateGraphRisk(@NonNull UUID entityId, double graphRisk, @NonNull Instant updatedAt);
    void upsertRelationship(@NonNull FraudRelationship relationship);
    @NonNull Optional<FraudRelationship> findRelationship(@NonNull UUID sourceId, @NonNull UUID targetId, @NonNull RelationshipType type);
    void recordRelationshipEvent(@NonNull FraudRelationshipEvent event);
}
