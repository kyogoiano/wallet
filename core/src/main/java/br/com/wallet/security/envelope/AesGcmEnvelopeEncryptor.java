package br.com.wallet.security.envelope;

import br.com.wallet.security.keymanagement.GeneratedDataKey;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Pure AES-256-GCM envelope encryption implementation (REQ-SEC-020, I-ENV-001, I-ENV-006).
 */
public final class AesGcmEnvelopeEncryptor implements EnvelopeEncryptor {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecureRandom secureRandom;

    public AesGcmEnvelopeEncryptor() {
        this(new SecureRandom());
    }

    public AesGcmEnvelopeEncryptor(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public CryptoEnvelope encrypt(
            byte[] plaintext,
            TenantId tenantId,
            OperationId operationId,
            KeyId keyId,
            GeneratedDataKey dataKey
    ) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(dataKey, "dataKey must not be null");

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        EnvelopeVersion version = EnvelopeVersion.WALLET_ENV_V1;
        EncryptionAlgorithm algorithm = EncryptionAlgorithm.AES_256_GCM;

        byte[] aad = CanonicalAad.compute(version, tenantId, operationId, keyId, algorithm);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(dataKey.plaintextDek().getEncoded(), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
            cipher.updateAAD(aad);
            byte[] ciphertextWithTag = cipher.doFinal(plaintext);

            return new CryptoEnvelope(
                    version,
                    tenantId,
                    operationId,
                    keyId,
                    algorithm,
                    new CryptoBytes(iv),
                    dataKey.wrappedDek(),
                    new CryptoBytes(ciphertextWithTag)
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM encryption failed", ex);
        }
    }
}
