package br.com.wallet.exceptions;

public class EventPublishException extends RuntimeException {
    public EventPublishException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
