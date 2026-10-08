package br.com.wallet.exceptions;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException() {
        super("Account Not Found!");
    }

}
