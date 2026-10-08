package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.infrasctructure.messaging.dlq.DlqEvent;
import br.com.wallet.infrasctructure.messaging.dlq.DlqFailureType;
import br.com.wallet.infrasctructure.messaging.dlq.DlqStatus;
import br.com.wallet.infrasctructure.persistence.DlqOperationsDao;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class DlqConsumer extends AbstractNatsConsumer<DlqEvent> {

    private static final String streamName = "commands_dlq";
    private static final String subject = "commands.dlq.*"; // Subject for transfer commands
    private static final String durableConsumerName = "dlq-consumer"; // Durable consumer name for JetStream
    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqOperationsDao dlqOperationsDao;

    public DlqConsumer(@Autowired final Connection natsConnection,
                       final DlqOperationsDao dlqOperationsDao) {
        super(subject, null, natsConnection, null, null);
        this.dlqOperationsDao = dlqOperationsDao;
    }

    @Override
    public void init() throws Exception {
        setupGeneralSubscription(streamName, durableConsumerName);
    }

    /**
     * This overrides default message processing for one exclusive to dlq support
     * @param message dlq message
     */
    @Override
    void processMessage(@NonNull final Message message) {
        try {
            var headers = message.getHeaders();

            var event = mapToDlqEvent(message, headers);

            dlqOperationsDao.insert(event);

            message.ack();

        } catch (Exception e) {
            log.error("Failed to persist DLQ event", e);

            // retry via JetStream
            message.nakWithDelay(Duration.ofSeconds(5));
        }
    }

    private DlqEvent mapToDlqEvent(@NonNull final Message message, @NonNull final Headers headers) {
        return new DlqEvent(
                UUID.randomUUID(),
                UUID.fromString(Objects.requireNonNull(headers.getFirst("operation_id"))),
                headers.getFirst("original_subject"),
                DlqStatus.PENDING,
                headers.getFirst("error_message"),
                new String(message.getData()),
                0,
                null,
                Instant.parse(Objects.requireNonNull(headers.getFirst("failed_at"))),
                null,
                DlqFailureType.from(headers.getFirst("failure_type"))
        );
    }
}
