package br.com.wallet.infrastructure.messaging.status;

import br.com.wallet.edge.api.LocalOperationStatusBroadcaster;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Cluster status fan-out consumer (TASK-5.9, REQ-EDG-019).
 * Subscribes to 'events.operations.status' on NATS, receiving status events
 * published by peer nodes, and delivers them to the local OperationStatusHub.
 */
@Component
@ConditionalOnBean(Connection.class)
public class NatsOperationStatusListener implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NatsOperationStatusListener.class);

    private final Connection connection;
    private final LocalOperationStatusBroadcaster localHub;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    private Dispatcher dispatcher;

    public NatsOperationStatusListener(
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
    public void afterPropertiesSet() {
        if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
            log.warn("NATS connection not ready; skipping status listener initialization");
            return;
        }

        try {
            dispatcher = connection.createDispatcher(this::onMessage);
            dispatcher.subscribe(NatsOperationStatusBroadcaster.STATUS_SUBJECT);
            connection.flush(java.time.Duration.ofSeconds(1));
            log.info("Initialized cluster status fan-out listener on topic '{}' (instanceId={})",
                    NatsOperationStatusBroadcaster.STATUS_SUBJECT, instanceId);
        } catch (Exception e) {
            log.error("Failed to subscribe status listener to NATS: {}", e.getMessage(), e);
        }
    }

    void onMessage(Message msg) {
        try {
            StatusEventMessage event = objectMapper.readValue(msg.getData(), StatusEventMessage.class);

            // Filter out echo messages from this same instance (already handled locally)
            if (event.sourceInstanceId() != null && event.sourceInstanceId().equals(instanceId)) {
                return;
            }

            log.info("Received cluster status event for opId={} status={} from peer={}",
                    event.operationId(), event.status(), event.sourceInstanceId());

            localHub.publishStatus(event.operationId(), event.status(), event.message());
        } catch (Exception e) {
            log.error("Failed to process cluster status message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        if (dispatcher != null && connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            try {
                connection.closeDispatcher(dispatcher);
            } catch (Exception ignored) {
            }
        }
    }
}
