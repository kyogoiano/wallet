package br.com.wallet.infrasctructure.messaging.publisher;

import br.com.wallet.exceptions.ExceptionType;
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
import java.time.Duration;

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

    public void replay(@NonNull final Message dlqMessage, @NonNull final Connection connection) {

        dlqMessage.getHeaders().add("replayed", "true");
        dlqMessage.getHeaders().add("replay_at", clock.instant().toString());
        final long deliveries = dlqMessage.metaData().deliveredCount();

        final var originalSubject = dlqMessage.getHeaders().getFirst("original_subject");

        final var replayMessage = NatsMessage.builder()
                .subject(originalSubject)
                .headers(dlqMessage.getHeaders())
                .data(dlqMessage.getData())
                .build();

        try {
            final JetStream jetStream = connection.jetStream();
            jetStream.publish(replayMessage);
        } catch (Exception ex) {
            log.error("Failed to publish to DLQ", ex);
            dlqMessage.nakWithDelay(retryDelay(deliveries));
        }
    }

    private Duration retryDelay(long deliveries) {
        return switch ((int) deliveries) {
            case 1 -> Duration.ofSeconds(1);
            case 2 -> Duration.ofSeconds(5);
            case 3 -> Duration.ofSeconds(10);
            default -> Duration.ofSeconds(30);
        };
    }
}
