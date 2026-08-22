package br.com.wallet.wallet.api.event;

import br.com.wallet.wallet.api.event.DomainEventType;

import java.io.IOException;

public interface EventPublisher {
    void publish(DomainEventType eventType, String payload) throws IOException;
}
