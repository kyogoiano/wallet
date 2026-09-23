package br.com.wallet.core.exceptions;

/**
 * Thrown when an operation requires a canonical tenant identifier but none was provided,
 * or when the tenant identifier fails canonical validation (I-DF20-004, I-SEC-010).
 */
public class TenantContextMissingException extends RuntimeException {

    public TenantContextMissingException(String message) {
        super(message);
    }

    public TenantContextMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}