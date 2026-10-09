package br.com.wallet.copilot.api.exception;

public class ProposalConflictException extends RuntimeException {
    public ProposalConflictException(String message) {
        super(message);
    }
}
