package br.com.wallet.ledger.api;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public interface OperationStateUseCase {

    void markOperationCompleted(@NonNull UUID operationId);

    void markOperationFailed(@NonNull UUID operationId, String errorMessage, String failureType);
}
