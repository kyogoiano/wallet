package br.com.wallet.copilot.api.exception;

public class ProposalExpiredException extends RuntimeException {
    public ProposalExpiredException(String message) {
        super(message);
    }
}
