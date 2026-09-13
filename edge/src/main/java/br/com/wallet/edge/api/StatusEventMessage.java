package br.com.wallet.edge.api;

import java.util.UUID;

/**
 * Clean contract payload for cluster-wide operation status fan-out over NATS (REQ-PRC-004, REQ-PRC-008).
 * Zero internal Core or database dependencies (I-CONTRACT-001).
 */
public record StatusEventMessage(
        UUID operationId,
        String status,
        long timestamp,
        String message,
        String sourceInstanceId
) {
    public StatusEventMessage(UUID operationId, String status, String message) {
        this(operationId, status, System.currentTimeMillis(), message, null);
    }
}
