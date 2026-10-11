package br.com.wallet.edge.api;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI for resolving durable operation status from the core repository or database before attaching SSE listeners.
 * Prevents race conditions and lost terminal event updates for reconnecting clients (I-EDGE-007).
 */
public interface DurableOperationStateProvider {

    /**
     * Resolves the current durable operation status, if persisted.
     *
     * @param operationId unique operation tracking ID
     * @return optional containing durable status if found
     */
    Optional<DurableOperationStatus> findOperationStatus(UUID operationId);
}
