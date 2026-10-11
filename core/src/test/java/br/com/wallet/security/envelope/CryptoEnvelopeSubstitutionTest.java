package br.com.wallet.security.envelope;

import br.com.wallet.security.failure.CryptographicIntegrityException;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.15: CryptoEnvelope Substitution & Ciphertext Tampering Test (REQ-SEC-020, I-ENV-002)")
class CryptoEnvelopeSubstitutionTest {

    private EnvelopeEncryptor encryptor;
    private EnvelopeDecryptor decryptor;
    private final SecureRandom secureRandom = new SecureRandom();

    private final TenantId tenantId = new TenantId("tenant-finance");
    private final KeyId keyId = new KeyId("kms-key-1");

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
    @DisplayName("Assert swapping ciphertext from Envelope B into Envelope A fails GCM authentication check")
    void shouldRejectCiphertextSplicing() {
        byte[] payloadA = "{\"command\":\"PAYMENT\",\"amount\":10.00}".getBytes(StandardCharsets.UTF_8);
        byte[] payloadB = "{\"command\":\"PAYMENT\",\"amount\":999999.00}".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey keyA = createDataKey();
             GeneratedDataKey keyB = createDataKey()) {

            CryptoEnvelope envA = encryptor.encrypt(payloadA, tenantId, new OperationId(UUID.randomUUID()), keyId, keyA);
            CryptoEnvelope envB = encryptor.encrypt(payloadB, tenantId, new OperationId(UUID.randomUUID()), keyId, keyB);

            // Attacker splices ciphertext of B into A
            CryptoEnvelope splicedEnv = new CryptoEnvelope(
                    envA.version(),
                    envA.tenantId(),
                    envA.operationId(),
                    envA.keyId(),
                    envA.algorithm(),
                    envA.iv(),
                    envA.wrappedDek(),
                    envB.ciphertext() // spliced from B!
            );

            assertThatThrownBy(() -> decryptor.decrypt(splicedEnv, keyA.plaintextDek()))
                    .isInstanceOf(CryptographicIntegrityException.class)
                    .hasMessageContaining("AEAD authentication tag verification failed");
        }
    }

    @Test
    @DisplayName("Assert swapping IV from Envelope B into Envelope A fails GCM authentication check")
    void shouldRejectIvSwapping() {
        byte[] payload = "{\"amount\":100.00}".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey key = createDataKey()) {
            CryptoEnvelope envA = encryptor.encrypt(payload, tenantId, new OperationId(UUID.randomUUID()), keyId, key);
            CryptoEnvelope envB = encryptor.encrypt(payload, tenantId, new OperationId(UUID.randomUUID()), keyId, key);

            // Attacker swaps IV from B into A
            CryptoEnvelope swappedIvEnv = new CryptoEnvelope(
                    envA.version(),
                    envA.tenantId(),
                    envA.operationId(),
                    envA.keyId(),
                    envA.algorithm(),
                    envB.iv(), // Swapped IV!
                    envA.wrappedDek(),
                    envA.ciphertext()
            );

            assertThatThrownBy(() -> decryptor.decrypt(swappedIvEnv, key.plaintextDek()))
                    .isInstanceOf(CryptographicIntegrityException.class)
                    .hasMessageContaining("AEAD authentication tag verification failed");
        }
    }
}
