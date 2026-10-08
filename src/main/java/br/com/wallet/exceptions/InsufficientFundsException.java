package br.com.wallet.exceptions;

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException() {
        super("Insufficient funds");
    }

    public InsufficientFundsException(String message) {
        super(message);
    }
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }

}
