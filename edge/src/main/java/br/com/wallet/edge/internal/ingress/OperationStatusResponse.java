package br.com.wallet.edge.internal.ingress;

import java.time.Instant;
import java.util.UUID;

/**
 * Real-time operation state payload pushed to frontend clients via Server-Sent Events (REQ-EDG-019).
 */
public record OperationStatusResponse(
        UUID operationId,
        String status,
        Instant timestamp,
        String message
) {
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public boolean isTerminal() {
        return STATUS_COMPLETED.equalsIgnoreCase(status) || STATUS_FAILED.equalsIgnoreCase(status);
    }
}
