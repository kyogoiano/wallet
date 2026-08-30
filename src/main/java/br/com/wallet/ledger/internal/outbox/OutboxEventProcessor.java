package br.com.wallet.ledger.internal.outbox;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.ledger.api.event.EventPublisher;
import br.com.wallet.ledger.api.utils.JsonUtils;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);

    private final EventPublisher publisher;
    private final OutboxDao<OutboxEvent> outboxDao;
    private final JsonUtils jsonUtils;

    public OutboxEventProcessor(final EventPublisher publisher,
                                final OutboxDao<OutboxEvent> outboxDao,
                                final JsonUtils jsonUtils) {
        this.publisher = publisher;
        this.outboxDao = outboxDao;
        this.jsonUtils = jsonUtils;
    }

    @Traceable("outbox.relay.event")
    public void processEvent(@NonNull final OutboxEvent event, @NonNull final Instant now) {
        try {
            jsonUtils.parseDomainEventPayload(event.eventType(), event.payload()); // parser pre-check

            publisher.publish(event.eventType(), event.payload(), event.aggregateId());

            outboxDao.markAsProcessed(event.id(), now);
            log.info("Outbox event marked as processed! id={}, at={}", event.id(), now);
        } catch (Exception ex) {
            int retryCount = event.retryCount() + 1;
            if (retryCount > 10) {
                outboxDao.markAsDead(event.id(), now);
                log.error("Outbox event moved to DLQ! Publish event id={}", event.id(), ex);
            } else {
                final var backoff = Duration.ofSeconds((long) Math.pow(2, retryCount));
                outboxDao.markFailed(event.id(), now.plus(backoff));
                log.warn("Outbox retry scheduled id={}, retryCount={}, backoff={}", event.id(), retryCount, backoff, ex);
            }
        }
    }
}
