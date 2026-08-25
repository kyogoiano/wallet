package br.com.wallet.savings.api.model;

public enum SavingsExecutionStatus {
    EXECUTED,
    SKIPPED_INSUFFICIENT_FUNDS,
    REJECTED_BY_FRAUD,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}
