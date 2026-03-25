package br.com.wallet.interfaces.rest.exception;

public enum ErrorCode {
    BAD_REQUEST("wallet.bad_request"),
    INTERNAL_ERROR("wallet.internal_error"),
    INSUFFICIENT_FUNDS("wallet.insufficient_funds"),
    INVALID_AMOUNT("wallet.invalid_amount");

    private final String error;

    ErrorCode(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
