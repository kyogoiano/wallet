package br.com.wallet.edge.api;

import java.util.concurrent.CompletableFuture;

/**
 * Publisher SPI dispatching commands to NATS JetStream with Nats-Msg-Id header (REQ-EDG-021).
 */
public interface EdgeCommandPublisher {

    /**
     * Publishes command to NATS JetStream.
     * Completes successfully when PUBACK is confirmed by broker.
     */
    CompletableFuture<Void> publish(CommandEnvelope command);
}
