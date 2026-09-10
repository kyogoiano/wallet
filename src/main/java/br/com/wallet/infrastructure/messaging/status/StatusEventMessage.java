package br.com.wallet.infrastructure.messaging.status;

import java.util.UUID;

/**
 * Message payload for cluster-wide operation status fan-out over NATS (TASK-5.9, REQ-EDG-019).
 */
public record StatusEventMessage(
        UUID operationId,
        String status,
        long timestamp,
        String message,
        String sourceInstanceId
) {
}
