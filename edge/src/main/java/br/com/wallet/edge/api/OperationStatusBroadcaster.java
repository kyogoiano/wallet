package br.com.wallet.edge.api;

import java.util.UUID;

/**
 * Public contract for broadcasting operation status transitions to connected edge clients (REQ-EDG-019).
 */
public interface OperationStatusBroadcaster {

    /**
     * Publishes a status transition (e.g. PROCESSING, COMPLETED, FAILED) for a given operation.
     *
     * @param operationId unique operation tracking ID
     * @param status      terminal or in-flight status string
     * @param message     human-readable detail or rejection reason
     */
    void publishStatus(UUID operationId, String status, String message);
}
