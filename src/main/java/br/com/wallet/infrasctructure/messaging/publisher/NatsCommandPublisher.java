package br.com.wallet.infrasctructure.messaging.publisher;

import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.domain.envelope.CommandEnvelope;
import br.com.wallet.exceptions.PermanentException;
import br.com.wallet.exceptions.TransientException;
import io.nats.client.*;
import io.nats.client.api.PublishAck;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Nats Command → Async processing → Event
 */
@Service
public class NatsCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsCommandPublisher.class);

    private final Connection connection;
    private final ObjectWriter commandWriter;

    public NatsCommandPublisher(final Connection connection,
                                final ObjectMapper objectMapper)
            throws IOException, JetStreamApiException {
        this.connection = connection;
        this.commandWriter = objectMapper.writerFor(CommandEnvelope.class);

        final var jsm = this.connection.jetStreamManagement();
        ensureStream(jsm, "commands", "commands.*", Duration.ofHours(24));
        ensureStream(jsm, "commands_dlq", "commands.dlq.*", Duration.ofDays(7));
    }

    private void ensureStream(@NonNull final JetStreamManagement jsm,
                              @NonNull final String streamName,
                              @NonNull final String subjects,
                              @NonNull final Duration retention)
            throws IOException, JetStreamApiException {

        try {
            jsm.getStreamInfo(streamName);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 404) {
                final var config = StreamConfiguration.builder()
                        .name(streamName)
                        .subjects(subjects)
                        .retentionPolicy(RetentionPolicy.Limits)
                        .maxAge(retention)
                        .storageType(StorageType.File)
                        .duplicateWindow(Duration.ofMinutes(5))
                        .build();
                jsm.addStream(config);
                log.info("Created JetStream '{}'", streamName);
            } else {
                throw e;
            }
        }
    }

    /**
     * Publishes a command asynchronously to NATS JetStream.
     * Returns a CompletableFuture that completes when the server ACKs the message.
     */
    public <T extends TraceContext> CompletableFuture<PublishAck> publishAsync(final String subject, final T command) {
        try {
            var operationId = command.operationId();
            final var envelope = new CommandEnvelope<>(
                    operationId,
                    command.getClass().getSimpleName(),
                    Instant.now(),
                    command
            );

            final var payload = commandWriter.writeValueAsBytes(envelope);

            final var headers = new Headers();
            headers.add("operation_id", operationId.toString());
            headers.add("type", envelope.type());
            headers.add("timestamp", envelope.timestamp().toString());
            headers.add("Nats-Msg-Id", operationId.toString());

            final var message = NatsMessage.builder()
                    .subject(subject)
                    .headers(headers)
                    .data(payload)
                    .build();

            final var jetStream = connection.jetStream();
            final var publishOptions = PublishOptions.builder()
                    .expectedStream("commands")
                    .build();

            // Return the native NATS future
            return jetStream.publishAsync(message, publishOptions)
                    .thenApply(ack -> {
                        log.debug("Published command [{}] to subject [{}], seqNo={}",
                                envelope.type(), subject, ack.getSeqno());
                        return ack;
                    })
                    .exceptionally(ex -> {
                        log.error("Failed to publish command to NATS: {}", ex.getMessage());
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to process request at the moment", ex);
                    });

        } catch (JacksonException e) {
            throw new PermanentException("Invalid command payload", e);
        } catch (IOException e) {
            throw new TransientException("Failed to initialize NATS publish", e);
        }
    }
}
