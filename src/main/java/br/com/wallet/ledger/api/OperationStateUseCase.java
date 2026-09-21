package br.com.wallet.ledger.api;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface OperationStateUseCase {

    void markOperationCompleted(@NonNull UUID operationId);

    void markOperationFailed(@NonNull UUID operationId, String errorMessage, String failureType);

    default void markOperationCompleted(@NonNull UUID operationId, @Nullable String tenantId) {
        markOperationCompleted(operationId);
    }

    default void markOperationFailed(@NonNull UUID operationId, String errorMessage, String failureType, @Nullable String tenantId) {
        markOperationFailed(operationId, errorMessage, failureType);
    }
}
