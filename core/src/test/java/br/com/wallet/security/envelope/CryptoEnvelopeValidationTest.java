package br.com.wallet.security.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.1: CryptoEnvelope Structural Validation Test (REQ-SEC-020, I-SEC-016)")
class CryptoEnvelopeValidationTest {

    private final EnvelopeVersion version = EnvelopeVersion.WALLET_ENV_V1;
    private final TenantId tenantId = new TenantId("tenant-corp");
    private final OperationId operationId = new OperationId(UUID.randomUUID());
    private final KeyId keyId = new KeyId("key-primary");
    private final EncryptionAlgorithm algorithm = EncryptionAlgorithm.AES_256_GCM;
    private final CryptoBytes validIv = new CryptoBytes(new byte[12]);
    private final CryptoBytes validWrappedDek = new CryptoBytes(new byte[32]);
    private final CryptoBytes validCiphertext = new CryptoBytes(new byte[16]); // exactly 16 bytes (auth tag)

    @Test
    @DisplayName("Assert valid parameters create CryptoEnvelope successfully")
    void shouldCreateValidEnvelope() {
        CryptoEnvelope envelope = new CryptoEnvelope(
                version, tenantId, operationId, keyId, algorithm, validIv, validWrappedDek, validCiphertext
        );

        assertThat(envelope.version()).isEqualTo(version);
        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.operationId()).isEqualTo(operationId);
        assertThat(envelope.keyId()).isEqualTo(keyId);
        assertThat(envelope.algorithm()).isEqualTo(algorithm);
        assertThat(envelope.iv().length()).isEqualTo(12);
        assertThat(envelope.wrappedDek().length()).isEqualTo(32);
        assertThat(envelope.ciphertext().length()).isEqualTo(16);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 11, 13, 16})
    @DisplayName("Assert IV length other than 12 bytes throws IllegalArgumentException (96-bit NIST requirement)")
    void shouldRejectInvalidIvLength(int length) {
        if (length == 12) return;
        CryptoBytes invalidIv = new CryptoBytes(new byte[length]);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, keyId, algorithm, invalidIv, validWrappedDek, validCiphertext
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GCM IV must be exactly 12 bytes");
    }

    @Test
    @DisplayName("Assert empty wrappedDek throws IllegalArgumentException")
    void shouldRejectEmptyWrappedDek() {
        CryptoBytes emptyDek = new CryptoBytes(new byte[0]);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, keyId, algorithm, validIv, emptyDek, validCiphertext
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrappedDek must not be empty");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 15})
    @DisplayName("Assert ciphertext shorter than 16 bytes throws IllegalArgumentException (minimum auth tag)")
    void shouldRejectShortCiphertext(int length) {
        CryptoBytes shortCiphertext = new CryptoBytes(new byte[length]);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, keyId, algorithm, validIv, validWrappedDek, shortCiphertext
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ciphertext must be at least 16 bytes");
    }

    @Test
    @DisplayName("Assert null fields throw NullPointerException")
    void shouldRejectNullFields() {
        assertThatThrownBy(() -> new CryptoEnvelope(
                null, tenantId, operationId, keyId, algorithm, validIv, validWrappedDek, validCiphertext
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, null, operationId, keyId, algorithm, validIv, validWrappedDek, validCiphertext
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, null, keyId, algorithm, validIv, validWrappedDek, validCiphertext
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, null, algorithm, validIv, validWrappedDek, validCiphertext
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, keyId, null, validIv, validWrappedDek, validCiphertext
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, keyId, algorithm, null, validWrappedDek, validCiphertext
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, keyId, algorithm, validIv, null, validCiphertext
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CryptoEnvelope(
                version, tenantId, operationId, keyId, algorithm, validIv, validWrappedDek, null
        )).isInstanceOf(NullPointerException.class);
    }
}
