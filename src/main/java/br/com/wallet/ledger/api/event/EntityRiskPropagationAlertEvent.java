package br.com.wallet.ledger.api.event;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event for outbox staging and NATS publication when an entity's propagated risk exceeds the alert threshold.
 */
public record EntityRiskPropagationAlertEvent(
    @NonNull UUID entityId,
    double propagatedRisk,
    int hopCount,
    @NonNull String strongestRelationship,
    @NonNull UUID rootSourceId,
    @NonNull Instant timestamp
) implements DomainEvent {

    public EntityRiskPropagationAlertEvent {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(strongestRelationship, "strongestRelationship cannot be null");
        Objects.requireNonNull(rootSourceId, "rootSourceId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    @Override
    public DomainEventType eventType() {
        return DomainEventType.RISK_PROPAGATION_DETECTED;
    }

    @Override
    public UUID aggregateId() {
        return entityId;
    }

    @Override
    public String aggregateType() {
        return "FRAUD_ENTITY";
    }

    @Override
    public UUID partitionKey() {
        return entityId;
    }
}
