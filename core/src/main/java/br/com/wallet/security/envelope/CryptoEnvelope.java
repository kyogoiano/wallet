package br.com.wallet.security.envelope;

import java.util.Objects;

/**
 * Immutable cryptographic envelope carrying encrypted financial command data and cryptographic metadata
 * across Edge and Core boundaries (REQ-SEC-020, I-ENV-001, I-SEC-016).
 */
public record CryptoEnvelope(
        EnvelopeVersion version,
        TenantId tenantId,
        OperationId operationId,
        KeyId keyId,
        EncryptionAlgorithm algorithm,
        CryptoBytes iv,
        CryptoBytes wrappedDek,
        CryptoBytes ciphertext
) {
    public CryptoEnvelope {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(iv, "iv must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");

        if (iv.length() != 12) {
            throw new IllegalArgumentException("GCM IV must be exactly 12 bytes (96 bits), found: " + iv.length());
        }
        if (wrappedDek.length() == 0) {
            throw new IllegalArgumentException("wrappedDek must not be empty");
        }
        if (ciphertext.length() < 16) {
            throw new IllegalArgumentException("ciphertext must be at least 16 bytes (auth tag), found: " + ciphertext.length());
        }
    }

    @Override
    public String toString() {
        return "CryptoEnvelope[version=" + version +
                ", tenantId=" + tenantId +
                ", operationId=" + operationId +
                ", keyId=" + keyId +
                ", algorithm=" + algorithm +
                ", ivLen=" + iv.length() +
                ", wrappedDekLen=" + wrappedDek.length() +
                ", ciphertextLen=" + ciphertext.length() + "]";
    }
}
