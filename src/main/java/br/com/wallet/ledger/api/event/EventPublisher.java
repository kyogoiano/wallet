package br.com.wallet.ledger.api.event;

import java.io.IOException;
import java.util.UUID;

public interface EventPublisher {
    void publish(DomainEventType eventType, String payload, UUID aggregateId) throws IOException;
}
