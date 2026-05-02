package br.com.wallet.exceptions;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException() {
        super("Account Not Found!");
    }

    public AccountNotFoundException(String message) {
        super(message);
    }
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
