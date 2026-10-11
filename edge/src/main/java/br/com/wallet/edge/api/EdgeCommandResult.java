package br.com.wallet.edge.api;

import java.util.UUID;

/**
 * Sealed result hierarchy returned by the Reactive Edge Gateway.
 * Mandated by PLAN-000.9 Section 2.
 */
public sealed interface EdgeCommandResult {

    record Accepted(UUID operationId, String location, boolean spooledDegraded) implements EdgeCommandResult {}

    record RateLimited(String reason, int retryAfterSeconds) implements EdgeCommandResult {}

    record BulkheadFull(int currentInflight, int maxInflight) implements EdgeCommandResult {}

    record ContentTooLarge(int payloadBytes, int maxBytes) implements EdgeCommandResult {}

    record Saturated(String reason, int retryAfterSeconds) implements EdgeCommandResult {}

    record Conflict(String reason) implements EdgeCommandResult {}

    record Rejected(String reason) implements EdgeCommandResult {}
}
