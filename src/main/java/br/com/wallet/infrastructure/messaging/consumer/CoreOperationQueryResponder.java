package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.edge.api.DurableOperationStatus;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Headless Core responder answering durable status queries from Edge (REQ-PRC-020, I-EDGE-007).
 * Receives Request-Reply queries on 'operations.query.*' and returns current ACID status.
 */
@Component
@ConditionalOnProperty(name = "wallet.runtime.mode", havingValue = "multi-process", matchIfMissing = true)
public class CoreOperationQueryResponder implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CoreOperationQueryResponder.class);

    public static final String DEFAULT_QUERY_SUBJECT = "operations.query.*";

    private final Connection connection;
    private final OperationQueryUseCase operationQueryUseCase;
    private final ObjectMapper objectMapper;
    private final String querySubject;
    private Dispatcher dispatcher;

    @Autowired
    public CoreOperationQueryResponder(
            @Autowired(required = false) Connection connection,
            OperationQueryUseCase operationQueryUseCase,
            ObjectMapper objectMapper,
            @Value("${wallet.nats.query.subject:" + DEFAULT_QUERY_SUBJECT + "}") String querySubject
    ) {
        this.connection = connection;
        this.operationQueryUseCase = operationQueryUseCase;
        this.objectMapper = objectMapper;
        this.querySubject = (querySubject != null && !querySubject.isBlank()) ? querySubject : DEFAULT_QUERY_SUBJECT;
    }

    public CoreOperationQueryResponder(
            Connection connection,
            OperationQueryUseCase operationQueryUseCase,
            ObjectMapper objectMapper
    ) {
        this(connection, operationQueryUseCase, objectMapper, DEFAULT_QUERY_SUBJECT);
    }

    @Override
    public void afterPropertiesSet() {
        if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
            return;
        }
        try {
            dispatcher = connection.createDispatcher(this::onMessage);
            dispatcher.subscribe(querySubject, "core-query-group");
            connection.flush(java.time.Duration.ofSeconds(1));
            log.info("Initialized CoreOperationQueryResponder on subject '{}' with queue group 'core-query-group'", querySubject);
        } catch (Exception e) {
            log.error("Failed to subscribe query responder to NATS: {}", e.getMessage(), e);
        }
    }

    void onMessage(Message msg) {
        if (msg.getReplyTo() == null || msg.getReplyTo().isBlank()) {
            return;
        }

        try {
            String subject = msg.getSubject();
            String opIdStr = subject.substring(subject.lastIndexOf('.') + 1);
            UUID operationId = UUID.fromString(opIdStr);

            var statusOpt = operationQueryUseCase.getOperationStatus(operationId);
            DurableOperationStatus durable;
            if (statusOpt.isPresent()) {
                var s = statusOpt.get();
                durable = new DurableOperationStatus(
                        s.operationId(),
                        s.status().name(),
                        s.updatedAt(),
                        s.errorMessage() != null ? s.errorMessage() : "State: " + s.status().name()
                );
            } else {
                // Authoritative PostgreSQL store does not contain this operation yet (in-flight or unknown)
                durable = new DurableOperationStatus(
                        operationId,
                        DurableOperationStatus.STATUS_NOT_FOUND,
                        java.time.Instant.now(),
                        "Operation not registered in Core durable store"
                );
            }
            byte[] responseData = objectMapper.writeValueAsBytes(durable);
            connection.publish(msg.getReplyTo(), responseData);
        } catch (Exception e) {
            log.error("Error answering operation status query: {}", e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        if (dispatcher != null && connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            try {
                connection.closeDispatcher(dispatcher);
                connection.flush(java.time.Duration.ofSeconds(1));
            } catch (Exception ignored) {}
        }
    }
}
