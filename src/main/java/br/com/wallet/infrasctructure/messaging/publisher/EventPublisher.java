package br.com.wallet.infrasctructure.messaging.publisher;

import br.com.wallet.domain.event.DomainEventType;

import java.io.IOException;

public interface EventPublisher {
    void publish(DomainEventType eventType, String payload) throws IOException;
}
