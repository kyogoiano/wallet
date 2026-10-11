package br.com.wallet.ledger.api.exceptions;

public class CommandException extends BusinessException {
    public CommandException(String message) {
        super(message);
    }
}
