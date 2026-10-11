package br.com.wallet.security.envelope;

import java.util.Objects;

/**
 * Strongly typed KMS Key identifier (REQ-SEC-020).
 */
public record KeyId(String value) {
    public KeyId {
        Objects.requireNonNull(value, "keyId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("keyId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
