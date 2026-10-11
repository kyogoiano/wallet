package br.com.wallet.security.envelope;

import br.com.wallet.security.failure.CryptographicIntegrityException;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Pure AES-256-GCM envelope decryption implementation with canonical AAD authentication (REQ-SEC-020, I-ENV-002, I-ENV-003).
 */
public final class AesGcmEnvelopeDecryptor implements EnvelopeDecryptor {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    @Override
    public byte[] decrypt(CryptoEnvelope envelope, SensitiveKeyMaterial plaintextDek) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(plaintextDek, "plaintextDek must not be null");

        if (envelope.algorithm() != EncryptionAlgorithm.AES_256_GCM) {
            throw new IllegalArgumentException("Unsupported envelope algorithm: " + envelope.algorithm());
        }

        byte[] aad = CanonicalAad.compute(envelope);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(plaintextDek.getEncoded(), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.iv().value());

            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            cipher.updateAAD(aad);
            return cipher.doFinal(envelope.ciphertext().value());
        } catch (GeneralSecurityException ex) {
            throw new CryptographicIntegrityException(
                    "AEAD authentication tag verification failed for operation: " + envelope.operationId(),
                    envelope.operationId(),
                    ex
            );
        }
    }
}
