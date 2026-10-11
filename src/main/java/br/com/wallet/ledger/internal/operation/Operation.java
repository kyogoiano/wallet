package br.com.wallet.ledger.internal.operation;

import br.com.wallet.core.exceptions.TenantContextMissingException;
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
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for Operation");
        }
    }
}
