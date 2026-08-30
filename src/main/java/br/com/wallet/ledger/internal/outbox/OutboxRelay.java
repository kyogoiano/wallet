package br.com.wallet.ledger.internal.outbox;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
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

    private final OutboxDao<OutboxEvent> outboxDao;
    private final Clock clock;
    private final OutboxEventProcessor eventProcessor;

    public OutboxRelay(final OutboxDao<OutboxEvent> outboxDao,
                       final Clock clock,
                       final OutboxEventProcessor eventProcessor) {
        this.outboxDao = outboxDao;
        this.clock = clock;
        this.eventProcessor = eventProcessor;
    }


    @Scheduled(fixedDelayString = "${wallet.outbox.relay.fixed-delay:10000}", initialDelayString = "${wallet.outbox.relay.initial-delay:1000}")
    @Traceable("outbox.process")
    @Transactional
    public void process() {
        final var now = clock.instant();

        final var events = outboxDao.claimBatch(now, 100);

        events.forEach(event -> eventProcessor.processEvent(event, now));
    }
}