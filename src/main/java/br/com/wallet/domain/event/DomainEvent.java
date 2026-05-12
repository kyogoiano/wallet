package br.com.wallet.domain.event;

import java.util.UUID;

public interface DomainEvent {
    DomainEventType eventType();
    UUID aggregateId();
    String aggregateType();
    UUID partitionKey();
}
