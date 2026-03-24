package br.com.wallet.infrasctructure.outbox;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.infrasctructure.messaging.EventPublisher;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * This is just an extension bridge,
 * so it simulates an outbox relay,
 * it'll query data to publish events every 10 seconds.
 * It includes the following features
 * - exponential retry
 * - concurrent lock (SKIP LOCKED)
 * - fine time control
 * - idempotence
 * - transactional consistency
 */

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final EventPublisher publisher;
    private final OutboxDao outboxDao;
    private final Clock clock;


    public OutboxRelay(final EventPublisher publisher,
                       final OutboxDao outboxDao,
                       final Clock clock) {
        this.publisher = publisher;
        this.outboxDao = outboxDao;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 10000)
    @Traceable("outbox.process")
    @Transactional
    public void process() {
        final var now = clock.instant();
        final var events = outboxDao.getOutboxEvents(now);

        for (final var event : events) {
            try {
                publisher.publish(event.eventType(), event.payload());

                outboxDao.markAsProcessed(event.id(), now);

            } catch (Exception e) {
                // used for retries
                outboxDao.markFailed(event.id(), now);

                log.warn("Failed to publish event id={}", event.id(), e);
            }
        }
    }
}