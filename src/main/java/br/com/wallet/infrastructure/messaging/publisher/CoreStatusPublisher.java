package br.com.wallet.infrastructure.messaging.publisher;

import br.com.wallet.edge.api.OperationStatusBroadcaster;
import br.com.wallet.edge.api.StatusEventMessage;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Headless Core status publisher (TASK-PRC-4.2, REQ-PRC-007).
 * Broadcasts terminal and intermediate status transitions over NATS JetStream
 * to 'operations.status.<operationId>' and 'events.operations.status' for distributed Edge fanout.
 */
@Component
public class CoreStatusPublisher implements OperationStatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(CoreStatusPublisher.class);
    public static final String STATUS_TOPIC_PREFIX = "operations.status.";

    private final Connection connection;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    public CoreStatusPublisher(
            @Autowired(required = false) Connection connection,
            ObjectMapper objectMapper,
            @Value("${wallet.instance-id:#{T(java.util.UUID).randomUUID().toString()}}") String instanceId
    ) {
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    @Override
    public void publishStatus(UUID operationId, String status, String message) {
        if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
            log.warn("NATS connection not ready; cannot publish status for opId={}", operationId);
            return;
        }

        try {
            StatusEventMessage event = new StatusEventMessage(
                    operationId,
                    status,
                    System.currentTimeMillis(),
                    message,
                    instanceId
            );
            byte[] data = objectMapper.writeValueAsBytes(event);

            // Canonical status subject per operation ID (REQ-PRC-007)
            String directSubject = STATUS_TOPIC_PREFIX + operationId;
            connection.publish(directSubject, data);

            log.debug("Published operation status opId={} status={} to {}",
                    operationId, status, directSubject);
        } catch (Exception e) {
            log.error("Failed to publish status event for opId {}: {}", operationId, e.getMessage(), e);
        }
    }
}
