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
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";
    public static final String STATUS_DEGRADED_UNAVAILABLE = "DEGRADED_UNAVAILABLE";

    public boolean isTerminal() {
        return STATUS_COMPLETED.equalsIgnoreCase(status)
                || STATUS_FAILED.equalsIgnoreCase(status)
                || STATUS_BLOCKED.equalsIgnoreCase(status);
    }

    public boolean isNotFound() {
        return STATUS_NOT_FOUND.equalsIgnoreCase(status);
    }

    public boolean isDegraded() {
        return STATUS_DEGRADED_UNAVAILABLE.equalsIgnoreCase(status);
    }
}
