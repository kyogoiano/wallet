package br.com.wallet.edge.api;


import java.util.concurrent.CompletableFuture;

/**
 * Public Edge Gateway Ingress Contract.
 */
public interface EdgeCommandIngress {

    /**
     * Non-blocking acceptance endpoint verifying durable persistence before returning 202 Accepted.
     */
    CompletableFuture<EdgeCommandResult> acceptCommand(CommandEnvelope command);
}
