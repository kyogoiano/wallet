package br.com.wallet.edge.internal.status;

import br.com.wallet.edge.api.DurableOperationStateProvider;
import br.com.wallet.edge.api.DurableOperationStatus;
import br.com.wallet.edge.internal.ingress.ConditionalOnEdgeIngress;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * NATS Request-Reply implementation of DurableOperationStateProvider (REQ-PRC-020, I-EDGE-007).
 * Queries Core for durable operation status over 'operations.query.<operationId>' with zero database connections.
 */
@Component
@ConditionalOnEdgeIngress
@ConditionalOnBean(Connection.class)
public class NatsDurableOperationStateProvider implements DurableOperationStateProvider {

    private static final Logger log = LoggerFactory.getLogger(NatsDurableOperationStateProvider.class);
    public static final String DEFAULT_QUERY_SUBJECT_PREFIX = "operations.query.";
    public static final String QUERY_SUBJECT_PREFIX = DEFAULT_QUERY_SUBJECT_PREFIX;
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofMillis(400);
    private static final int MAX_ATTEMPTS = 2;

    private final Connection connection;
    private final ObjectMapper objectMapper;
    private final String querySubjectPrefix;

    @Autowired
    public NatsDurableOperationStateProvider(
            Connection connection,
            ObjectMapper objectMapper,
            @Value("${wallet.nats.query.prefix:" + DEFAULT_QUERY_SUBJECT_PREFIX + "}") String querySubjectPrefix
    ) {
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.querySubjectPrefix = (querySubjectPrefix != null && !querySubjectPrefix.isBlank())
                ? querySubjectPrefix
                : DEFAULT_QUERY_SUBJECT_PREFIX;
    }

    public NatsDurableOperationStateProvider(Connection connection, ObjectMapper objectMapper) {
        this(connection, objectMapper, DEFAULT_QUERY_SUBJECT_PREFIX);
    }

    @Override
    public Optional<DurableOperationStatus> findOperationStatus(UUID operationId) {
        if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
            log.warn("NATS connection not ready; returning degraded durable status for opId={}", operationId);
            return Optional.of(new DurableOperationStatus(
                    operationId,
                    DurableOperationStatus.STATUS_DEGRADED_UNAVAILABLE,
                    java.time.Instant.now(),
                    "NATS IPC connection unavailable"
            ));
        }

        String subject = querySubjectPrefix + operationId;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Message response = connection.request(subject, null, ATTEMPT_TIMEOUT);
                if (response != null && response.getData() != null && response.getData().length > 0) {
                    DurableOperationStatus status = objectMapper.readValue(response.getData(), DurableOperationStatus.class);
                    log.debug("Resolved durable status for opId={}: {}", operationId, status.status());
                    return Optional.of(status);
                }
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed to query durable status for opId={} over NATS: {}",
                        attempt, MAX_ATTEMPTS, operationId, e.getMessage());
            }
        }

        // Bounded retry exhausted: return explicit degraded status (REQ-PRC-020)
        return Optional.of(new DurableOperationStatus(
                operationId,
                DurableOperationStatus.STATUS_DEGRADED_UNAVAILABLE,
                java.time.Instant.now(),
                "Core durable status query timed out after " + MAX_ATTEMPTS + " attempts"
        ));
    }
}
