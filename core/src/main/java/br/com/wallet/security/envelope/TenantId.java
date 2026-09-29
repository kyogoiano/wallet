package br.com.wallet.security.envelope;

import java.util.Objects;

/**
 * Strongly typed tenant identifier for cryptographic envelope binding (REQ-SEC-020, I-ENV-002).
 */
public record TenantId(String value) {
    public TenantId {
        Objects.requireNonNull(value, "tenantId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("tenantId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
