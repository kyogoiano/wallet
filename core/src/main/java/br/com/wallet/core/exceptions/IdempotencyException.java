package br.com.wallet.core.exceptions;

public class IdempotencyException extends RuntimeException {
    public IdempotencyException(String message) {
        super(message);
    }
}
