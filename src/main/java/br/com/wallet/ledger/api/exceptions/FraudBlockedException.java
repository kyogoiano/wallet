package br.com.wallet.ledger.api.exceptions;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FraudBlockedException extends RuntimeException {
    private final UUID operationId;
    private final UUID userId;

    public FraudBlockedException(@NonNull UUID operationId, @NonNull UUID userId) {
        super("Transaction blocked due to high fraud risk, operationId: " + operationId + ", userId: " + userId + ".");
        this.operationId = operationId;
        this.userId = userId;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public UUID getUserId() {
        return userId;
    }
}
