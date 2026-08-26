package br.com.wallet.infrastructure.rest.exception;

public enum ErrorCode {
    BAD_REQUEST("wallet.bad_request"),
    NOT_FOUND("wallet.not_found"),
    INTERNAL_ERROR("wallet.internal_error"),
    INSUFFICIENT_FUNDS("wallet.insufficient_funds"),
    INVALID_AMOUNT("wallet.invalid_amount"),
    MISSING_HEADER("wallet.missing_header"),
    VALIDATION_ERROR("wallet.validation_error"),
    DUPLICATE_OPERATION("wallet.duplicate_operation"),
    FRAUD_BLOCKED("wallet.fraud_blocked"),
    ACCOUNT_BLOCKED("wallet.account_blocked");

    private final String error;

    ErrorCode(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
