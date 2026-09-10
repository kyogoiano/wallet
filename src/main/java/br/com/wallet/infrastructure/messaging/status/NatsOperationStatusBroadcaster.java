package br.com.wallet.infrastructure.messaging.status;

import br.com.wallet.edge.api.LocalOperationStatusBroadcaster;
import br.com.wallet.edge.api.OperationStatusBroadcaster;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Cluster-aware status broadcaster implementing NATS-backed fan-out (TASK-5.9, REQ-EDG-019).
 * Broadcasts status updates directly to the local edge node's SSE hub, and publishes to the
 * shared NATS topic 'events.operations.status' so other edge nodes in the cluster deliver
 * the event to their local subscribers.
 */
@Component
@Primary
@ConditionalOnBean(Connection.class)
public class NatsOperationStatusBroadcaster implements OperationStatusBroadcaster {

    public static final String STATUS_SUBJECT = "events.operations.status";
    private static final Logger log = LoggerFactory.getLogger(NatsOperationStatusBroadcaster.class);

    private final Connection connection;
    private final LocalOperationStatusBroadcaster localHub;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    public NatsOperationStatusBroadcaster(
            Connection connection,
            LocalOperationStatusBroadcaster localHub,
            ObjectMapper objectMapper,
            @Value("${edge.instance-id:#{T(java.util.UUID).randomUUID().toString()}}") String instanceId
    ) {
        this.connection = connection;
        this.localHub = localHub;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    @Override
    public void publishStatus(UUID operationId, String status, String message) {
        // 1. Deliver immediately to local node subscribers (zero additional latency)
        if (localHub != null) {
            localHub.publishStatus(operationId, status, message);
        }

        // 2. Publish to NATS for cluster-wide peer fan-out
        if (connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            try {
                StatusEventMessage event = new StatusEventMessage(
                        operationId,
                        status,
                        System.currentTimeMillis(),
                        message,
                        instanceId
                );
                byte[] data = objectMapper.writeValueAsBytes(event);
                connection.publish(STATUS_SUBJECT, data);
            } catch (Exception e) {
                log.error("Failed to publish status event to NATS for opId {}: {}", operationId, e.getMessage(), e);
            }
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
