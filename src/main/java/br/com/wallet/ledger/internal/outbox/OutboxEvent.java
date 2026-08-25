package br.com.wallet.ledger.internal.outbox;

import br.com.wallet.ledger.api.event.DomainEvent;
import br.com.wallet.ledger.api.event.DomainEventType;

import java.util.UUID;

public record OutboxEvent(UUID id, DomainEventType eventType, String payload, Integer retryCount, UUID aggregateId, String aggregateType, UUID partitionKey) implements DomainEvent {

}
