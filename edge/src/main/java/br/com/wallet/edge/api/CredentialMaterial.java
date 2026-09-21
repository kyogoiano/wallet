package br.com.wallet.edge.api;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Secret key material wrapper enforcing defensive copying and zero string leakage (TASK-SEC-2.1).
 */
public record CredentialMaterial(byte[] secret) {

    public CredentialMaterial(final byte @NonNull [] secret) {
        Objects.requireNonNull(secret, "secret must not be null");
        this.secret = secret.clone();
    }

    @Override
    public byte @NonNull [] secret() {
        return secret.clone();
    }

    @Override
    public @NonNull String toString() {
        return "CredentialMaterial[REDACTED]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CredentialMaterial that = (CredentialMaterial) o;
        return Arrays.equals(secret, that.secret);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(secret);
    }
}
