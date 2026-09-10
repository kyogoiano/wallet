package br.com.wallet.infrastructure.messaging.publisher;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Production implementation of EdgeCommandPublisher dispatching commands to NATS JetStream (REQ-EDG-021).
 * Injects Nats-Msg-Id for broker-side deduplication (I-DEDUP-001) and completes only upon PublishAck (I-EDGE-001).
 */
@Component
@Primary
public class NatsEdgeCommandPublisher implements EdgeCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsEdgeCommandPublisher.class);
    private static final String STREAM_NAME = "commands";

    private final Connection connection;
    private final ObjectMapper objectMapper;

    public NatsEdgeCommandPublisher(Connection connection, ObjectMapper objectMapper) {
        this.connection = connection;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<Void> publish(CommandEnvelope command) {
        try {
            String subject = mapSubject(command.type());
            byte[] enrichedPayload = enrichPayload(command);

            Headers headers = new Headers();
            headers.add("Nats-Msg-Id", command.operationId().toString());
            headers.add("operation_id", command.operationId().toString());
            headers.add("type", command.type().name());
            headers.add("timestamp", String.valueOf(command.timestamp()));
            headers.add("client_ip", command.clientIp());
            headers.add("tenant_id", command.tenantId());

            NatsMessage message = NatsMessage.builder()
                    .subject(subject)
                    .headers(headers)
                    .data(enrichedPayload)
                    .build();

            JetStream jetStream = connection.jetStream();
            PublishOptions options = PublishOptions.builder()
                    .expectedStream(STREAM_NAME)
                    .build();

            return jetStream.publishAsync(message, options)
                    .thenAccept(ack -> {
                        log.debug("Published command [{}] to [{}] seqNo={}", command.type(), subject, ack.getSeqno());
                    });
        } catch (Exception e) {
            log.error("Failed to initiate NATS publish for opId={}: {}", command.operationId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String mapSubject(CommandType type) {
        return switch (type) {
            case TRANSFER -> "commands.wallet.transfer";
            case DEPOSIT -> "commands.wallet.deposit";
            case WITHDRAW -> "commands.wallet.withdraw";
        };
    }

    private byte[] enrichPayload(CommandEnvelope command) {
        try {
            JsonNode tree = objectMapper.readTree(command.payloadJson());
            if (tree instanceof ObjectNode objectNode) {
                if (!objectNode.has("operationId")) {
                    objectNode.put("operationId", command.operationId().toString());
                }
                return objectMapper.writeValueAsBytes(objectNode);
            }
            return command.payloadJson().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return command.payloadJson().getBytes(StandardCharsets.UTF_8);
        }
    }
}
