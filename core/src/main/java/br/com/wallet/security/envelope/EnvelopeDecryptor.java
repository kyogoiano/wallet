package br.com.wallet.security.envelope;

import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;

/**
 * Envelope decryption engine interface for core processing pre-gate (REQ-SEC-020, I-ENV-003).
 */
public interface EnvelopeDecryptor {

    /**
     * Decrypts a {@link CryptoEnvelope} and verifies its canonical AAD and GCM authentication tag.
     *
     * @param envelope The immutable cryptographic envelope.
     * @param plaintextDek The sensitive plaintext DEK obtained from KMS unwrap.
     * @return The verified decrypted plaintext payload bytes.
     * @throws br.com.wallet.security.failure.CryptographicIntegrityException if tag verification or AAD fails.
     */
    byte[] decrypt(CryptoEnvelope envelope, SensitiveKeyMaterial plaintextDek);
}
