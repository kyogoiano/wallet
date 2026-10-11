package br.com.wallet.security.envelope;

import br.com.wallet.security.failure.CryptographicIntegrityException;
import br.com.wallet.security.failure.KeyManagementUnavailableException;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.KeyContext;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.15: Cross-Tenant Envelope Substitution & AAD Tampering Security Test (I-ENV-002)")
class CrossTenantEnvelopeSubstitutionTest {

    private EnvelopeEncryptor encryptor;
    private EnvelopeDecryptor decryptor;
    private final SecureRandom secureRandom = new SecureRandom();

    private final TenantId tenantAlpha = new TenantId("tenant-alpha");
    private final TenantId tenantBeta = new TenantId("tenant-beta");
    private final KeyId keyId = new KeyId("kms-key-global");

    @BeforeEach
    void setUp() {
        this.encryptor = new AesGcmEnvelopeEncryptor();
        this.decryptor = new AesGcmEnvelopeDecryptor();
    }

    private GeneratedDataKey createDataKey() {
        byte[] rawPlain = new byte[32];
        secureRandom.nextBytes(rawPlain);
        byte[] rawWrapped = new byte[48];
        secureRandom.nextBytes(rawWrapped);
        return new GeneratedDataKey(new SensitiveKeyMaterial(rawPlain), new CryptoBytes(rawWrapped));
    }

    @Test
    @DisplayName("Assert swapping tenantId in AAD causes GCM authentication tag verification failure (I-ENV-002)")
    void shouldRejectTenantIdSubstitutionInAad() {
        byte[] plaintext = "{\"from\":\"acc-1\",\"to\":\"acc-2\",\"amount\":500.00}".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey dataKey = createDataKey()) {
            CryptoEnvelope authenticEnvelope = encryptor.encrypt(
                    plaintext,
                    tenantAlpha,
                    new OperationId(UUID.randomUUID()),
                    keyId,
                    dataKey
            );

            // Attacker swaps tenantAlpha with tenantBeta in envelope metadata
            CryptoEnvelope substitutedEnvelope = new CryptoEnvelope(
                    authenticEnvelope.version(),
                    tenantBeta, // Substituted!
                    authenticEnvelope.operationId(),
                    authenticEnvelope.keyId(),
                    authenticEnvelope.algorithm(),
                    authenticEnvelope.iv(),
                    authenticEnvelope.wrappedDek(),
                    authenticEnvelope.ciphertext()
            );

            // Decryption with the valid DEK fails because AAD does not match
            assertThatThrownBy(() -> decryptor.decrypt(substitutedEnvelope, dataKey.plaintextDek()))
                    .isInstanceOf(CryptographicIntegrityException.class)
                    .hasMessageContaining("AEAD authentication tag verification failed");
        }
    }

    @Test
    @DisplayName("Assert swapping operationId in AAD causes GCM authentication tag verification failure (I-ENV-002)")
    void shouldRejectOperationIdSubstitutionInAad() {
        byte[] plaintext = "{\"amount\":1000.00}".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey dataKey = createDataKey()) {
            CryptoEnvelope authenticEnvelope = encryptor.encrypt(
                    plaintext,
                    tenantAlpha,
                    new OperationId(UUID.randomUUID()),
                    keyId,
                    dataKey
            );

            // Attacker swaps operationId in envelope
            CryptoEnvelope substitutedEnvelope = new CryptoEnvelope(
                    authenticEnvelope.version(),
                    authenticEnvelope.tenantId(),
                    new OperationId(UUID.randomUUID()), // Different OpId!
                    authenticEnvelope.keyId(),
                    authenticEnvelope.algorithm(),
                    authenticEnvelope.iv(),
                    authenticEnvelope.wrappedDek(),
                    authenticEnvelope.ciphertext()
            );

            assertThatThrownBy(() -> decryptor.decrypt(substitutedEnvelope, dataKey.plaintextDek()))
                    .isInstanceOf(CryptographicIntegrityException.class)
                    .hasMessageContaining("AEAD authentication tag verification failed");
        }
    }
}
