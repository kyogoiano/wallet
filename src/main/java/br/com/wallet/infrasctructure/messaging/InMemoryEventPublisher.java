package br.com.wallet.infrasctructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);

    @Override
    public void publish(String eventType, String payload) {
        log.info("📤 Publishing event. type={}, payload={}", eventType, payload);
    }
}
