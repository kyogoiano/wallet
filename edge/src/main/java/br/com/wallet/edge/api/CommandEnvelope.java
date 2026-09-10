package br.com.wallet.edge.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable command envelope carrying client financial command data across the edge.
 */
public record CommandEnvelope(
        UUID operationId,
        CommandType type,
        String payloadJson,
        long timestamp,
        String clientIp,
        String tenantId
) {
    public CommandEnvelope {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(payloadJson, "payloadJson must not be null");
        if (clientIp == null) clientIp = "127.0.0.1";
        if (tenantId == null) tenantId = "default";
    }

    public static CommandEnvelope create(UUID operationId, CommandType type, String payloadJson, String clientIp) {
        return new CommandEnvelope(operationId, type, payloadJson, System.currentTimeMillis(), clientIp, "default");
    }

    public static CommandEnvelope create(UUID operationId, CommandType type, String payloadJson, String clientIp, String tenantId) {
        return new CommandEnvelope(operationId, type, payloadJson, System.currentTimeMillis(), clientIp, tenantId);
    }
}
