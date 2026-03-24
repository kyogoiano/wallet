package br.com.wallet.domain.event;

import java.util.UUID;

public interface DomainEvent {
    String eventType();
    UUID aggregateId();
    String aggregateType();
    UUID partitionKey();
}
