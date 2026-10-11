package br.com.wallet.integration.outbox.publisher;

import br.com.wallet.ledger.api.event.DomainEventType;
import br.com.wallet.ledger.api.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile({"test", "fail"})
public class FailingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(FailingEventPublisher.class);

    private final AtomicInteger failCount = new AtomicInteger(0);

    public void failNext(int times) {
        failCount.set(times);
    }

    @Override
    public void publish(DomainEventType eventType, String payload, UUID aggregateId) {

        if (failCount.getAndDecrement() > 0) {
            throw new RuntimeException("Simulated failure");
        }
        log.info("📤 Publishing event. type={}, payload={}", eventType.name(), payload);
    }
}
