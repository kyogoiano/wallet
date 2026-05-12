package br.com.wallet.infrasctructure.messaging.publisher;

import br.com.wallet.domain.event.DomainEventType;
import br.com.wallet.exceptions.EventPublishException;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.PublishOptions;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

@Component
public class NatsEventPublisher implements EventPublisher, JetStreamConfig {

    private final Logger log = LoggerFactory.getLogger(NatsEventPublisher.class);
    private final Connection connection;
    private final ObjectReader objectReader;
    private final Clock clock;

    public NatsEventPublisher(final Connection connection, final ObjectMapper objectMapper, final Clock clock) throws JetStreamApiException, IOException {
        this.connection = connection;
        this.objectReader = objectMapper.reader();
        this.clock = clock;

        final var jsm = this.connection.jetStreamManagement();
        ensureStream(jsm, "events", "events.*", Duration.ofHours(24));
    }

    

    @Override
    public void publish(@NonNull final DomainEventType eventType, final @NonNull String payload) throws IOException {
        final var headers = new Headers();
        headers.add("type", eventType.name());
        headers.add("version", "v1"); // used for future extensions
        headers.add("created_at", clock.instant().toString());

        // 🔥 importante para idempotência
        final var eventNode = objectReader.readTree(payload);

        final var operationId = eventNode.get("operationId").asString();
        headers.add("Nats-Msg-Id", operationId); // dedup JetStream
        final var message = NatsMessage.builder()
                .subject(eventType.getSubject())
                .headers(headers)
                .data(payload.getBytes(StandardCharsets.UTF_8))
                .build();

        final var publishOptions = PublishOptions.builder()
                .expectedStream("events")
                .build();
        final var jetStream = connection.jetStream();
        jetStream.publishAsync(message, publishOptions).thenApply(ack -> {
                    log.debug("Published event [{}] to subject [{}], seqNo={}",
                            eventType, eventType.getSubject(), ack.getSeqno());
                    return ack;
                })
                .exceptionally(ex -> {
                    log.error("Failed to publish event to NATS: {}", ex.getMessage());
                    throw new EventPublishException("Unable to process request at the moment", ex);
                });
    }
}