package br.com.wallet.edge.internal.status;

import br.com.wallet.edge.api.LocalOperationStatusBroadcaster;
import br.com.wallet.edge.api.StatusEventMessage;
import br.com.wallet.edge.internal.ingress.ConditionalOnEdgeIngress;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Cluster status fan-out consumer in Edge Gateway (REQ-PRC-008, I-EDGE-007).
 * Subscribes to 'operations.status.*' and 'events.operations.status' on NATS,
 * receiving status events published by Core workers or peer nodes,
 * and delivers them to the local OperationStatusHub for SSE push.
 */
@Component
@ConditionalOnEdgeIngress
@ConditionalOnBean(Connection.class)
public class NatsOperationStatusListener implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NatsOperationStatusListener.class);
    public static final String DIRECT_STATUS_SUBJECT = "operations.status.*";

    private final Connection connection;
    private final LocalOperationStatusBroadcaster localHub;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    private Dispatcher dispatcher;

    public NatsOperationStatusListener(
            Connection connection,
            @Autowired(required = false) LocalOperationStatusBroadcaster localHub,
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
            dispatcher.subscribe(DIRECT_STATUS_SUBJECT);
            connection.flush(java.time.Duration.ofSeconds(1));
            log.info("Initialized cluster status fan-out listener on canonical topic '{}' (instanceId={})",
                    DIRECT_STATUS_SUBJECT, instanceId);
        } catch (Exception e) {
            log.error("Failed to subscribe status listener to NATS: {}", e.getMessage(), e);
        }
    }

    void onMessage(Message msg) {
        try {
            StatusEventMessage event = objectMapper.readValue(msg.getData(), StatusEventMessage.class);

            // Filter out echo messages from this same instance
            if (event.sourceInstanceId() != null && event.sourceInstanceId().equals(instanceId)) {
                return;
            }

            log.info("Received cluster status event for opId={} status={} from peer/core={}",
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
