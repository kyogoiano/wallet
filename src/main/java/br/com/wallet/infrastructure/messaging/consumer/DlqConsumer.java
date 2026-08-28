package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.dlq.api.DlqManagementUseCase;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
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
public class DlqConsumer extends AbstractNatsConsumer {

    private static final String streamName = "commands_dlq";
    private static final String subject = "commands.dlq.*"; // Subject for transfer commands
    private static final String durableConsumerName = "dlq-consumer"; // Durable consumer name for JetStream
    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqManagementUseCase dlqManagementUseCase;

    public DlqConsumer(@Autowired final Connection natsConnection,
                       @Autowired final DlqManagementUseCase dlqManagementUseCase) {
        super(subject, natsConnection);
        this.dlqManagementUseCase = Objects.requireNonNull(dlqManagementUseCase, "dlqManagementUseCase cannot be null");
    }

    @Override
    public void init() throws Exception {
        setupGeneralSubscription(streamName, durableConsumerName);
        log.debug("DlqConsumer init started");
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

            dlqManagementUseCase.recordDlqEvent(event);

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
                headers.getFirst("userId") != null ? UUID.fromString(headers.getFirst("userId")) : null,
                headers.getFirst("original_subject"),
                DlqStatus.PENDING,
                headers.getFirst("error_message"),
                new String(message.getData()),
                0,
                null,
                Instant.parse(Objects.requireNonNull(headers.getFirst("failed_at"))),
                null,
                DlqFailureType.from(headers.getFirst("failure_type")),
                headers.getFirst("type")
        );
    }
}
