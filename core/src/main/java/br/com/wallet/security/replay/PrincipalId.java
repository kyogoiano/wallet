package br.com.wallet.security.replay;

import java.util.Objects;

/**
 * Strongly typed principal identifier for perimeter client credentials (REQ-SEC-028).
 */
public record PrincipalId(String value) {
    public PrincipalId {
        Objects.requireNonNull(value, "principalId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("principalId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
