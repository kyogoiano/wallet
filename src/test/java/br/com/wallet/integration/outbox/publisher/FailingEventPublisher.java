package br.com.wallet.integration.outbox.publisher;

import br.com.wallet.infrasctructure.messaging.publisher.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Primary
public class FailingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(FailingEventPublisher.class);

    private final AtomicInteger failCount = new AtomicInteger(0);

    public void failNext(int times) {
        failCount.set(times);
    }

    @Override
    public void publish(String eventType, String payload) {
        if (failCount.getAndDecrement() > 0) {
            throw new RuntimeException("Simulated failure");
        }
        log.info("📤 Publishing event. type={}, payload={}", eventType, payload);
    }
}
