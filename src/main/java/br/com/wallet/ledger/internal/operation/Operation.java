package br.com.wallet.ledger.internal.operation;

import br.com.wallet.ledger.api.domain.OperationStatus;
import java.time.Instant;
import java.util.UUID;

public record Operation(
        UUID id,
        OperationStatus status,
        String errorMessage,
        String failureType,
        Instant createdAt,
        Instant updatedAt,
        String tenantId
) {
    public Operation {
        if (tenantId == null) {
            tenantId = "default";
        }
    }

    public Operation(UUID id, OperationStatus status) {
        this(id, status, null, null, Instant.now(), Instant.now(), "default");
    }

    public Operation(UUID id, OperationStatus status, String errorMessage, String failureType, Instant createdAt, Instant updatedAt) {
        this(id, status, errorMessage, failureType, createdAt, updatedAt, "default");
    }
}
