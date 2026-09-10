package br.com.wallet.dlq.internal.engine;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import br.com.wallet.ledger.api.exceptions.TransientException;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

@Component
public class DlqReplayEngine {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayEngine.class);
    private final Clock clock;
    private final DlqOperationsDao dlqDao;
    private final Connection connection;

    public DlqReplayEngine(
            @NonNull final Clock clock,
            @NonNull final DlqOperationsDao dlqDao,
            @NonNull final Connection connection
    ) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.dlqDao = Objects.requireNonNull(dlqDao, "dlqDao cannot be null");
        this.connection = Objects.requireNonNull(connection, "connection cannot be null");
    }

    @Scheduled(fixedDelayString = "${wallet.dlq.replay.fixed-delay:10000}", initialDelayString = "${wallet.dlq.replay.initial-delay:10000}")
    @Transactional
    public void process() {
        final var now = clock.instant();
        log.info("Retry engine process started! at={}", now);

        final var batch = dlqDao.claimBatch(now, 50);

        for (final var event : batch) {
            try {
                if (event.status() == DlqStatus.COMPLETED || event.status() == DlqStatus.EXHAUSTED || event.status() == DlqStatus.DISCARDED) {
                    continue;
                }
                replay(event);
                dlqDao.markAsCompleted(event.id(), now);

            } catch (TransientException e) {
                // retry with exponential backoff (transitions to EXHAUSTED if retry >= 3)
                dlqDao.markFailed(
                        event.id(),
                        now,
                        DlqFailureType.TRANSIENT
                );

            } catch (Exception e) {
                // fails a poison event (transitions to EXHAUSTED if retry >= 3)
                dlqDao.markFailed(event.id(), now, DlqFailureType.POISON);
            }
        }
    }

    @Traceable("dlq.auto_replay")
    private void replay(@NonNull final DlqEvent event) throws JetStreamApiException, IOException {
        final var headers = new Headers();

        headers.add("operation_id", event.operationId().toString());
        if (event.userId() != null) {
            headers.add("userId", event.userId().toString());
        }
        headers.add("replayed", "true");
        headers.add("replay_count", String.valueOf(event.retryCount()));
        headers.add("type", event.eventType());
        final var message = NatsMessage.builder()
                .subject(event.subject())
                .headers(headers)
                .data(event.payload().getBytes())
                .build();

        final JetStream jetStream = connection.jetStream();
        jetStream.publish(message);
    }
}
