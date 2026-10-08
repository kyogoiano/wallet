package br.com.wallet.exceptions;

public enum ExceptionType {
    BUSINESS, TRANSIENT, PERMANENT, UNKNOWN;

    public static ExceptionType parseException(final Exception ex) {
        return switch (ex) {
            case BusinessException e  -> BUSINESS;
            case TransientException e -> TRANSIENT;
            case PermanentException e -> PERMANENT;
            case null, default        -> UNKNOWN;
        };
    }
}

