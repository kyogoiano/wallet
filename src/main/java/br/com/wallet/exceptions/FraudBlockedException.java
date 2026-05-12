package br.com.wallet.exceptions;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FraudBlockedException extends RuntimeException {

    public FraudBlockedException(@NonNull UUID operationId, @NonNull UUID userId) {
        super("Transaction blocked due to high fraud risk, operationId: " + operationId + ", userId: " + userId + ".");
    }
}
