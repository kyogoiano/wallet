package br.com.wallet.infrastructure.messaging.publisher;

import br.com.wallet.ledger.api.event.DomainEventType;
import br.com.wallet.ledger.api.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "in-memory"})
public class InMemoryEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);

    @Override
    public void publish(DomainEventType eventType, String payload) {
        log.info("📤 Publishing event. type={}, payload={}", eventType.name(), payload);
    }
}
