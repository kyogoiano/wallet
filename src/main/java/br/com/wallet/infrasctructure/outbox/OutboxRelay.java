package br.com.wallet.infrasctructure.outbox;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.domain.event.*;
import br.com.wallet.infrasctructure.messaging.publisher.EventPublisher;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.utils.JsonUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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
    private final JsonUtils jsonUtils;


    public OutboxRelay(final EventPublisher publisher,
                       final OutboxDao outboxDao,
                       final Clock clock, JsonUtils jsonUtils) {
        this.publisher = publisher;
        this.outboxDao = outboxDao;
        this.clock = clock;
        this.jsonUtils = jsonUtils;
    }

    @Scheduled(fixedDelay = 10000)
    @Traceable("outbox.process")
    @Transactional
    public void process() {
        final var now = clock.instant();

        final var events = outboxDao.claimBatch(now, 100);

        for (final var event : events) {
            processSingleEvent(event, now);
        }
    }

    private void processSingleEvent(@NonNull OutboxEvent event, @NonNull Instant now) {
        try {
            final var domainEventType = this.resolveDomainEventType(event.eventType());
            jsonUtils.parseDomainEventPayload(domainEventType, event.payload());

            publisher.publish(domainEventType, event.payload());

            outboxDao.markAsProcessed(event.id(), now);
            log.info("Outbox event marked as processed! id={}, at={}", event.id(), now);
        } catch (Exception ex) {

            int retryCount = event.retryCount() + 1;
            if (retryCount > 10) {
                outboxDao.markAsDead(event.id(), now);
                log.error("Outbox event moved to DLQ! Publish event id={}", event.id(), ex);
            } else {

                final var backoff = Duration.ofSeconds((long) Math.pow(2, retryCount));
                // used for retries
                outboxDao.markFailed(event.id(), now.plus(backoff));
                log.warn("Outbox retry scheduled id={}, retryCount={}, backoff={}", event.id(), retryCount, backoff, ex);
            }
        }
    }

    private DomainEventType resolveDomainEventType(@NonNull final String eventType) {
        return Optional.ofNullable(DomainEventType.fromString(eventType))
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventType));
    }
}