package br.com.wallet.security.envelope;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed operation identifier linking the cryptographic envelope to client idempotency key (REQ-SEC-020).
 */
public record OperationId(UUID value) {
    public OperationId {
        Objects.requireNonNull(value, "operationId value must not be null");
    }

    public static OperationId fromString(String uuidString) {
        Objects.requireNonNull(uuidString, "uuidString must not be null");
        return new OperationId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
