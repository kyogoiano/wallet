package br.com.wallet.copilot.api.model;

public enum ProposalStatus {
    PROPOSED,
    EXECUTING,
    EXECUTED,
    REJECTED,
    EXPIRED,
    INVALIDATED;

    public boolean isTerminal() {
        return this == EXECUTED || this == REJECTED || this == EXPIRED || this == INVALIDATED;
    }
}
