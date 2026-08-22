package br.com.wallet.wallet.api.event;

import java.io.IOException;

public interface EventPublisher {
    void publish(DomainEventType eventType, String payload) throws IOException;
}
