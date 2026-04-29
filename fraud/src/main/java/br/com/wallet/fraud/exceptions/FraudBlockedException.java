package br.com.wallet.fraud.exceptions;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class FraudBlockedException extends RuntimeException {


    public FraudBlockedException(@NonNull UUID operationId, @NonNull String userId, @NonNull String targetedUserId) {
        super("Transaction blocked due to high fraud risk, operationId: " + operationId + ", userId: " + userId + ", targetedUserId: " + targetedUserId + ".");
    }
}
