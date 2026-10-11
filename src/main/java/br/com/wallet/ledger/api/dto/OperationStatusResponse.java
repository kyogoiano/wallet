package br.com.wallet.ledger.api.dto;

import br.com.wallet.ledger.api.domain.OperationStatus;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record OperationStatusResponse(
        @NonNull UUID operationId,
        @NonNull OperationStatus status,
        String errorMessage,
        String failureType,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {
}
