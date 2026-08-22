package br.com.wallet.wallet.api.exceptions;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException() {
        super("Account Not Found!");
    }

}
