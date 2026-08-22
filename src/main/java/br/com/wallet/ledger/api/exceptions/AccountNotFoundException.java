package br.com.wallet.ledger.api.exceptions;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException() {
        super("Account Not Found!");
    }

}
