package br.com.wallet.security.envelope;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable cryptographic byte container that enforces defensive copying on construction
 * and value extraction (I-SEC-016).
 */
public record CryptoBytes(byte[] value) {

    public CryptoBytes {
        Objects.requireNonNull(value, "value must not be null");
        value = value.clone();
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    public int length() {
        return value.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CryptoBytes other)) return false;
        return Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "CryptoBytes[length=" + value.length + "]";
    }
}
