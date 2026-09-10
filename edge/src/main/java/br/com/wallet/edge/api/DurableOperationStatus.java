package br.com.wallet.edge.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable operation state record queried before establishing real-time SSE subscriptions (I-EDGE-007).
 */
public record DurableOperationStatus(
        UUID operationId,
        String status,
        Instant timestamp,
        String message
) {
    public boolean isTerminal() {
        return "COMPLETED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "BLOCKED".equalsIgnoreCase(status);
    }
}
