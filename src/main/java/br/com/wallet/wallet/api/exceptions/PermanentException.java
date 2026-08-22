package br.com.wallet.wallet.api.exceptions;

public class PermanentException extends RuntimeException {
    public PermanentException(String message) {
        super(message);
    }

    public PermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
