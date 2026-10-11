package br.com.wallet.security.envelope;

import br.com.wallet.security.failure.MalformedEnvelopeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EnvelopeCodec Binary Serialization Test (REQ-SEC-020, I-ENV-001)")
class EnvelopeCodecTest {

    @Test
    @DisplayName("Assert encode and decode round-trip reproduces identical CryptoEnvelope")
    void shouldEncodeAndDecodeSuccessfully() {
        UUID opId = UUID.randomUUID();
        CryptoEnvelope original = new CryptoEnvelope(
                EnvelopeVersion.WALLET_ENV_V1,
                new TenantId("tenant-super-corp"),
                new OperationId(opId),
                new KeyId("master-kms-key-42"),
                EncryptionAlgorithm.AES_256_GCM,
                new CryptoBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}),
                new CryptoBytes(new byte[]{20, 21, 22, 23}),
                new CryptoBytes(new byte[32])
        );

        byte[] encoded = EnvelopeCodec.encode(original);
        assertThat(encoded).isNotEmpty();

        CryptoEnvelope decoded = EnvelopeCodec.decode(encoded);

        assertThat(decoded.version()).isEqualTo(original.version());
        assertThat(decoded.tenantId()).isEqualTo(original.tenantId());
        assertThat(decoded.operationId()).isEqualTo(original.operationId());
        assertThat(decoded.keyId()).isEqualTo(original.keyId());
        assertThat(decoded.algorithm()).isEqualTo(original.algorithm());
        assertThat(decoded.iv().value()).isEqualTo(original.iv().value());
        assertThat(decoded.wrappedDek().value()).isEqualTo(original.wrappedDek().value());
        assertThat(decoded.ciphertext().value()).isEqualTo(original.ciphertext().value());
    }

    @Test
    @DisplayName("Assert corrupted magic bytes throw MalformedEnvelopeException")
    void shouldRejectCorruptMagic() {
        byte[] invalid = new byte[48]; // zeroes so magic 0x0 != MAGIC
        assertThatThrownBy(() -> EnvelopeCodec.decode(invalid))
                .isInstanceOf(MalformedEnvelopeException.class)
                .hasMessageContaining("Invalid envelope magic");
    }
}
