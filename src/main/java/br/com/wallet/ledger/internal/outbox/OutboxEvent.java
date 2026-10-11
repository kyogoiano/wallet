package br.com.wallet.ledger.internal.outbox;

import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.ledger.api.event.DomainEvent;
import br.com.wallet.ledger.api.event.DomainEventType;

import java.util.Map;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        DomainEventType eventType,
        String payload,
        Integer retryCount,
        UUID aggregateId,
        String aggregateType,
        UUID partitionKey
) implements DomainEvent, TraceContext {

    @Override
    public UUID operationId() {
        return aggregateId;
    }

    @Override
    public UUID userId() {
        return null;
    }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "event.id", id != null ? id.toString() : "unknown",
                "event.type", eventType != null ? eventType.name() : "unknown"
        );
    }
}
