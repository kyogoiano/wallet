package br.com.wallet.ledger.api;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public interface OperationStateUseCase {

    void markOperationCompleted(@NonNull UUID operationId, @NonNull String tenantId);

    void markOperationFailed(@NonNull UUID operationId, String errorMessage, String failureType, @NonNull String tenantId);

    default void markOperationCompleted(@NonNull UUID operationId) {
        markOperationCompleted(operationId, "tenant-alpha");
    }

    default void markOperationFailed(@NonNull UUID operationId, String errorMessage, String failureType) {
        markOperationFailed(operationId, errorMessage, failureType, "tenant-alpha");
    }
}
