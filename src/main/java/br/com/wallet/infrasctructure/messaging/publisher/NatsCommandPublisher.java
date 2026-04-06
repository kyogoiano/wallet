package br.com.wallet.infrasctructure.messaging.publisher;

import br.com.wallet.domain.envelope.CommandEnvelope;
import br.com.wallet.exceptions.PermanentException;
import br.com.wallet.exceptions.TransientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.PublishOptions;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Nats Command → Async processing → Event
 */
@Service
public class NatsCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsCommandPublisher.class);

    private final JetStream jetStream;
    private final ObjectMapper objectMapper;

    public NatsCommandPublisher(final Connection connection,
                                final ObjectMapper objectMapper)
            throws IOException, JetStreamApiException {

        this.objectMapper = objectMapper;
        this.jetStream = connection.jetStream();

        ensureStream(connection);
    }

    private void ensureStream(final Connection connection)
            throws IOException, JetStreamApiException {

        final var jsm = connection.jetStreamManagement();
        String streamName = "commands";

        try {
            jsm.getStreamInfo(streamName);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 404) {

                final var config = StreamConfiguration.builder()
                        .name(streamName)
                        .subjects("commands.*")
                        .retentionPolicy(RetentionPolicy.Limits)
                        .maxAge(Duration.ofHours(24))
                        .storageType(StorageType.File)
                        .build();

                jsm.addStream(config);

                log.info("Created JetStream '{}'", streamName);
            } else {
                throw e;
            }
        }
    }

    public <T> void publish(final String subject,
                            final UUID operationId,
                            final T command) {

        try {
            final var envelope = new CommandEnvelope<>(
                    operationId,
                    command.getClass().getSimpleName(),
                    Instant.now(),
                    command
            );

            final var payload = objectMapper.writeValueAsBytes(envelope);

            final var headers = new Headers();
            headers.add("operation_id", operationId.toString());
            headers.add("type", envelope.type());
            headers.add("timestamp", envelope.timestamp().toString());

            final var message = NatsMessage.builder()
                    .subject(subject)
                    .headers(headers)
                    .data(payload)
                    .build();

            final var publishOptions = PublishOptions.builder()
                    .expectedStream("commands") // safety check
                    .build();

            final var ack = jetStream.publish(message, publishOptions);

            if (ack == null || ack.getSeqno() <= 0) {
                throw new IllegalStateException("Invalid JetStream ACK");
            }

            log.debug("Published command [{}] to subject [{}], seqNo={}",
                    envelope.type(), subject, ack.getSeqno());

        } catch (JsonProcessingException e) {
            // serialization bug → DO NOT retry
            throw new PermanentException("Invalid command payload", e);

        } catch (IOException | JetStreamApiException e) {
            // infra failure → let caller decide
            throw new TransientException("Failed to publish command", e);
        }
    }
}