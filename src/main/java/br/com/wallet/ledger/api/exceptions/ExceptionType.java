package br.com.wallet.ledger.api.exceptions;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.core.exceptions.IdempotencyException;

public enum ExceptionType {
    BUSINESS, TRANSIENT, PERMANENT, UNKNOWN;

    public static ExceptionType parseException(final Exception ex) {
        return switch (ex) {
            case BusinessException e -> BUSINESS;
            case AccountBlockedException e -> BUSINESS;
            case IdempotencyException e -> BUSINESS;
            case FraudBlockedException e -> BUSINESS;
            case IllegalArgumentException e -> BUSINESS;
            case ReplayAttackException e -> PERMANENT;
            case PermanentException e -> PERMANENT;
            case TransientException e -> TRANSIENT;
            case null -> UNKNOWN;
            default -> TRANSIENT;
        };
    }
}
