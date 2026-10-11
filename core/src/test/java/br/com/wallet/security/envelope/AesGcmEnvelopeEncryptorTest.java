package br.com.wallet.security.envelope;

import br.com.wallet.security.failure.CryptographicIntegrityException;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.6: Pure AES-256-GCM Envelope Encryption & Decryption Engine Test (REQ-SEC-020, I-ENV-001, I-ENV-006)")
class AesGcmEnvelopeEncryptorTest {

    private EnvelopeEncryptor encryptor;
    private EnvelopeDecryptor decryptor;
    private final SecureRandom secureRandom = new SecureRandom();

    @BeforeEach
    void setUp() {
        this.encryptor = new AesGcmEnvelopeEncryptor();
        this.decryptor = new AesGcmEnvelopeDecryptor();
    }

    private GeneratedDataKey createDataKey() {
        byte[] rawPlaintext = new byte[32];
        secureRandom.nextBytes(rawPlaintext);
        byte[] rawWrapped = new byte[48];
        secureRandom.nextBytes(rawWrapped);
        return new GeneratedDataKey(new SensitiveKeyMaterial(rawPlaintext), new CryptoBytes(rawWrapped));
    }

    @Test
    @DisplayName("Assert positive encryption and decryption round-trip restores exact plaintext")
    void shouldEncryptAndDecryptSuccessfully() {
        TenantId tenantId = new TenantId("tenant-bravo");
        OperationId operationId = new OperationId(UUID.randomUUID());
        KeyId keyId = new KeyId("kek-master-1");
        byte[] plaintext = "{\"transferId\":\"123\",\"amount\":500.00}".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey dataKey = createDataKey()) {
            CryptoEnvelope envelope = encryptor.encrypt(plaintext, tenantId, operationId, keyId, dataKey);

            assertThat(envelope.version()).isEqualTo(EnvelopeVersion.WALLET_ENV_V1);
            assertThat(envelope.tenantId()).isEqualTo(tenantId);
            assertThat(envelope.operationId()).isEqualTo(operationId);
            assertThat(envelope.keyId()).isEqualTo(keyId);
            assertThat(envelope.algorithm()).isEqualTo(EncryptionAlgorithm.AES_256_GCM);
            assertThat(envelope.iv().length()).isEqualTo(12);
            assertThat(envelope.wrappedDek()).isEqualTo(dataKey.wrappedDek());
            assertThat(envelope.ciphertext().length()).isGreaterThanOrEqualTo(plaintext.length + 16);

            // Decrypt using same plaintext DEK
            byte[] decrypted = decryptor.decrypt(envelope, dataKey.plaintextDek());
            assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("{\"transferId\":\"123\",\"amount\":500.00}");
        }
    }

    @Test
    @DisplayName("Assert tampered ciphertext throws CryptographicIntegrityException")
    void shouldRejectTamperedCiphertext() {
        TenantId tenantId = new TenantId("tenant-bravo");
        OperationId operationId = new OperationId(UUID.randomUUID());
        KeyId keyId = new KeyId("kek-master-1");
        byte[] plaintext = "payload-data".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey dataKey = createDataKey()) {
            CryptoEnvelope envelope = encryptor.encrypt(plaintext, tenantId, operationId, keyId, dataKey);

            // Tamper 1 byte of ciphertext
            byte[] tamperedCipher = envelope.ciphertext().value();
            tamperedCipher[0] ^= 0x01;
            CryptoEnvelope tamperedEnvelope = new CryptoEnvelope(
                    envelope.version(),
                    envelope.tenantId(),
                    envelope.operationId(),
                    envelope.keyId(),
                    envelope.algorithm(),
                    envelope.iv(),
                    envelope.wrappedDek(),
                    new CryptoBytes(tamperedCipher)
            );

            assertThatThrownBy(() -> decryptor.decrypt(tamperedEnvelope, dataKey.plaintextDek()))
                    .isInstanceOf(CryptographicIntegrityException.class)
                    .hasMessageContaining("AEAD authentication tag verification failed");
        }
    }

    @Test
    @DisplayName("Assert altered tenantId in envelope throws CryptographicIntegrityException via canonical AAD mismatch")
    void shouldRejectAlteredTenantInAad() {
        TenantId originalTenant = new TenantId("tenant-alpha");
        TenantId attackerTenant = new TenantId("tenant-attacker");
        OperationId operationId = new OperationId(UUID.randomUUID());
        KeyId keyId = new KeyId("kek-master-1");
        byte[] plaintext = "transfer-payload".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey dataKey = createDataKey()) {
            CryptoEnvelope envelope = encryptor.encrypt(plaintext, originalTenant, operationId, keyId, dataKey);

            // Attacker swaps tenantId in envelope
            CryptoEnvelope swappedEnvelope = new CryptoEnvelope(
                    envelope.version(),
                    attackerTenant,
                    envelope.operationId(),
                    envelope.keyId(),
                    envelope.algorithm(),
                    envelope.iv(),
                    envelope.wrappedDek(),
                    envelope.ciphertext()
            );

            assertThatThrownBy(() -> decryptor.decrypt(swappedEnvelope, dataKey.plaintextDek()))
                    .isInstanceOf(CryptographicIntegrityException.class)
                    .hasMessageContaining("AEAD authentication tag verification failed");
        }
    }

    @Test
    @DisplayName("Assert decrypting with wrong DEK throws CryptographicIntegrityException")
    void shouldRejectWrongDek() {
        TenantId tenantId = new TenantId("tenant-bravo");
        OperationId operationId = new OperationId(UUID.randomUUID());
        KeyId keyId = new KeyId("kek-master-1");
        byte[] plaintext = "transfer-payload".getBytes(StandardCharsets.UTF_8);

        try (GeneratedDataKey key1 = createDataKey();
             GeneratedDataKey key2 = createDataKey()) {
            CryptoEnvelope envelope = encryptor.encrypt(plaintext, tenantId, operationId, keyId, key1);

            assertThatThrownBy(() -> decryptor.decrypt(envelope, key2.plaintextDek()))
                    .isInstanceOf(CryptographicIntegrityException.class);
        }
    }

    @Test
    @DisplayName("Assert each encryption generates a distinct 12-byte CSPRNG IV (I-ENV-006)")
    void shouldGenerateUniqueIvPerEncryption() {
        TenantId tenantId = new TenantId("tenant-bravo");
        OperationId operationId = new OperationId(UUID.randomUUID());
        KeyId keyId = new KeyId("kek-master-1");
        byte[] plaintext = "sample".getBytes(StandardCharsets.UTF_8);

        Set<String> ivSet = new HashSet<>();
        try (GeneratedDataKey dataKey = createDataKey()) {
            for (int i = 0; i < 1000; i++) {
                CryptoEnvelope envelope = encryptor.encrypt(plaintext, tenantId, operationId, keyId, dataKey);
                String hexIv = java.util.HexFormat.of().formatHex(envelope.iv().value());
                assertThat(ivSet.add(hexIv)).isTrue();
            }
        }
    }
}
