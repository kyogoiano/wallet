package br.com.wallet.infrastructure.messaging.publisher;

import br.com.wallet.ledger.api.exceptions.ExceptionType;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);
    private final Clock clock = Clock.systemUTC();

    public void handleDlqMessage(@NonNull String subject, @NonNull Connection connection, @NonNull Message message, @NonNull Exception error) {
        final var now = clock.instant();
        final var newHeaders = new Headers();

        newHeaders.add("original_subject", message.getSubject());
        newHeaders.add("operation_id", message.getHeaders().getFirst("operation_id"));
        newHeaders.add("type", message.getHeaders().getFirst("type"));
        newHeaders.add("failed_at", now.toString());
        newHeaders.add("error", error.getClass().getSimpleName());
        newHeaders.add("error_message", error.getMessage());
        newHeaders.add("delivery_count", String.valueOf(message.metaData().deliveredCount()));
        newHeaders.add("failure_type", ExceptionType.parseException(error).name());
        newHeaders.add("Nats-Msg-Id", message.getHeaders().getFirst("Nats-Msg-Id"));
        newHeaders.add("userId", message.getHeaders().getFirst("userId"));

        final var dlqMessage = NatsMessage.builder()
                .subject(subject)
                .headers(newHeaders)
                .data(message.getData())
                .build();

        try {
            final JetStream jetStream = connection.jetStream();
            jetStream.publish(dlqMessage);
        } catch (Exception ex) {
            log.error("Failed to publish to DLQ", ex);
        }
    }

    public io.nats.client.api.PublishAck publishDlqConfirmed(
            @NonNull String subject,
            @NonNull Connection connection,
            @NonNull Message message,
            @NonNull Exception error
    ) throws Exception {
        final var now = clock.instant();
        final var newHeaders = new Headers();

        newHeaders.add("original_subject", message.getSubject());
        String opId = message.getHeaders() != null ? message.getHeaders().getFirst("operation_id") : null;
        if (opId != null) newHeaders.add("operation_id", opId);
        String type = message.getHeaders() != null ? message.getHeaders().getFirst("type") : null;
        if (type != null) newHeaders.add("type", type);
        newHeaders.add("failed_at", now.toString());
        newHeaders.add("error", error.getClass().getSimpleName());
        newHeaders.add("error_message", error.getMessage());
        long deliveredCount = (message.metaData() != null) ? message.metaData().deliveredCount() : 1;
        newHeaders.add("delivery_count", String.valueOf(deliveredCount));
        newHeaders.add("failure_type", ExceptionType.parseException(error).name());
        String msgId = message.getHeaders() != null ? message.getHeaders().getFirst("Nats-Msg-Id") : null;
        if (msgId != null) newHeaders.add("Nats-Msg-Id", msgId);
        String userId = message.getHeaders() != null ? message.getHeaders().getFirst("userId") : null;
        if (userId != null) newHeaders.add("userId", userId);

        final var dlqMessage = NatsMessage.builder()
                .subject(subject)
                .headers(newHeaders)
                .data(message.getData())
                .build();

        final JetStream jetStream = connection.jetStream();
        return jetStream.publish(dlqMessage);
    }
}
