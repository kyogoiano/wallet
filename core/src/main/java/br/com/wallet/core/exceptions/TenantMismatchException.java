package br.com.wallet.core.exceptions;

import org.jspecify.annotations.Nullable;

/**
 * Thrown when an operation attempts cross-tenant resource access or when
 * participating account tenants do not match the command tenant (I-SEC-005).
 */
public class TenantMismatchException extends RuntimeException {

    private final String expectedTenantId;
    private final String actualTenantId;

    public TenantMismatchException(String message) {
        super(message);
        this.expectedTenantId = null;
        this.actualTenantId = null;
    }

    public TenantMismatchException(String message, @Nullable String expectedTenantId, @Nullable String actualTenantId) {
        super(message);
        this.expectedTenantId = expectedTenantId;
        this.actualTenantId = actualTenantId;
    }

    public TenantMismatchException(String message, Throwable cause) {
        super(message, cause);
        this.expectedTenantId = null;
        this.actualTenantId = null;
    }

    @Nullable
    public String getExpectedTenantId() {
        return expectedTenantId;
    }

    @Nullable
    public String getActualTenantId() {
        return actualTenantId;
    }
}
