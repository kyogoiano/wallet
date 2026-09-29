package br.com.wallet.edge.internal.publisher;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.internal.ingress.ConditionalOnEdgeIngress;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Production implementation of EdgeCommandPublisher dispatching commands to NATS JetStream (REQ-EDG-021, REQ-PRC-006).
 * Injects Nats-Msg-Id for broker-side deduplication (I-DEDUP-001) and completes only upon PublishAck (I-EDGE-001).
 */
@Component
@Primary
@ConditionalOnEdgeIngress
@ConditionalOnBean(Connection.class)
public class NatsEdgeCommandPublisher implements EdgeCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsEdgeCommandPublisher.class);
    private static final String STREAM_NAME = "commands";

    private final Connection connection;
    private final ObjectMapper objectMapper;
    private final br.com.wallet.security.envelope.EnvelopeEncryptor envelopeEncryptor;
    private final br.com.wallet.security.keymanagement.KeyManagementClient keyManagementClient;

    public NatsEdgeCommandPublisher(Connection connection, ObjectMapper objectMapper) {
        this(connection, objectMapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NatsEdgeCommandPublisher(
            Connection connection,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) br.com.wallet.security.envelope.EnvelopeEncryptor envelopeEncryptor,
            @org.springframework.beans.factory.annotation.Autowired(required = false) br.com.wallet.security.keymanagement.KeyManagementClient keyManagementClient
    ) {
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.envelopeEncryptor = envelopeEncryptor;
        this.keyManagementClient = keyManagementClient;
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
            headers.add("principal_id", command.principalId());
            headers.add("key_id", command.keyId());
            headers.add("publisher_id", "edge-gateway");

            if (envelopeEncryptor != null && keyManagementClient != null) {
                br.com.wallet.security.envelope.TenantId tenantId = new br.com.wallet.security.envelope.TenantId(command.tenantId());
                String keyIdStr = (command.keyId() != null && !command.keyId().isBlank() && !"unknown".equals(command.keyId()))
                        ? command.keyId() : "default-key";
                br.com.wallet.security.envelope.KeyId keyId = new br.com.wallet.security.envelope.KeyId(keyIdStr);
                br.com.wallet.security.keymanagement.KeyContext context = br.com.wallet.security.keymanagement.KeyContext.forTenant(tenantId);

                try (br.com.wallet.security.keymanagement.GeneratedDataKey dataKey = keyManagementClient.generateDataKey(tenantId, keyId, context)) {
                    br.com.wallet.security.envelope.CryptoEnvelope envelope = envelopeEncryptor.encrypt(
                            enrichedPayload,
                            tenantId,
                            new br.com.wallet.security.envelope.OperationId(command.operationId()),
                            keyId,
                            dataKey
                    );
                    enrichedPayload = br.com.wallet.security.envelope.EnvelopeCodec.encode(envelope);
                    headers.add("content_type", "application/x-wallet-crypto-envelope");
                    headers.add("envelope_version", envelope.version().code());
                }
            }

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
                    .thenAccept(ack -> log.debug("Published command [{}] to [{}] seqNo={}", command.type(), subject, ack.getSeqno()));
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
