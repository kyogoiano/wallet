package br.com.wallet.infrasctructure.messaging.dlq;

import br.com.wallet.exceptions.TransientException;
import br.com.wallet.infrasctructure.persistence.DlqOperationsDao;
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

@Component
public class DlqReplayEngine {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayEngine.class);
    private final Clock clock;
    private final DlqOperationsDao dlqDao;
    private final JetStream jetStream;

    public DlqReplayEngine(Clock clock, DlqOperationsDao dlqDao, JetStream jetStream) {
        this.clock = clock;
        this.dlqDao = dlqDao;
        this.jetStream = jetStream;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void process() {
        final var now = clock.instant();
        log.info("Retry engine process started! at={} ", now);

        final var batch = dlqDao.claimBatch(now, 50);

        for (final var event : batch) {
            try {
                replay(event);
                dlqDao.markAsCompleted(event.id(), now);

            } catch (TransientException e) {
                // retry with exponential backoff
                dlqDao.markFailed(
                        event.id(),
                        now,
                        DlqFailureType.TRANSIENT
                );

            } catch (Exception e) {
                // fails a poison event
                dlqDao.markFailed(event.id(), now, DlqFailureType.POISON);
            }
        }
    }

    private void replay(@NonNull final DlqEvent event) throws JetStreamApiException, IOException {
        final var headers = new Headers();

        headers.add("operation_id", event.operationId().toString());
        headers.add("replayed", "true");
        headers.add("replay_count", String.valueOf(event.retryCount()));

        final var message = NatsMessage.builder()
                .subject(event.subject())
                .headers(headers)
                .data(event.payload().getBytes())
                .build();

        jetStream.publish(message);
    }
}
