package br.com.wallet.edge.api;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable command envelope carrying client financial command data and cryptographically verified
 * tenant/principal identity across the edge and NATS messaging fabric (REQ-SEC-006, TASK-SEC-3.4).
 */
public record CommandEnvelope(
        UUID operationId,
        CommandType type,
        String payloadJson,
        long timestamp,
        String clientIp,
        String tenantId,
        String principalId,
        String keyId
) {
    public CommandEnvelope {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(payloadJson, "payloadJson must not be null");
        if (clientIp == null) clientIp = "127.0.0.1";
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for CommandEnvelope");
        }
        if (principalId == null) principalId = "unknown";
        if (keyId == null) keyId = "unknown";
    }

    public CommandEnvelope(
            UUID operationId,
            CommandType type,
            String payloadJson,
            long timestamp,
            String clientIp,
            String tenantId
    ) {
        this(operationId, type, payloadJson, timestamp, clientIp, tenantId, "unknown", "unknown");
    }

    public CommandEnvelope(
            UUID operationId,
            CommandType type,
            String payloadJson,
            long timestamp,
            String clientIp
    ) {
        this(operationId, type, payloadJson, timestamp, clientIp, "tenant-alpha", "unknown", "unknown");
    }

    public static CommandEnvelope create(UUID operationId, CommandType type, String payloadJson, String clientIp) {
        return new CommandEnvelope(operationId, type, payloadJson, System.currentTimeMillis(), clientIp, "tenant-alpha", "unknown", "unknown");
    }

    public static CommandEnvelope create(UUID operationId, CommandType type, String payloadJson, String clientIp, String tenantId) {
        return new CommandEnvelope(operationId, type, payloadJson, System.currentTimeMillis(), clientIp, tenantId, "unknown", "unknown");
    }

    public static CommandEnvelope create(
            UUID operationId,
            CommandType type,
            String payloadJson,
            String clientIp,
            String tenantId,
            String principalId,
            String keyId
    ) {
        return new CommandEnvelope(operationId, type, payloadJson, System.currentTimeMillis(), clientIp, tenantId, principalId, keyId);
    }
}
