package br.com.wallet.security.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-10.1: CryptoEnvelope & CryptoBytes Immutability Test (REQ-SEC-020, I-SEC-016)")
class CryptoEnvelopeImmutabilityTest {

    @Test
    @DisplayName("Assert CryptoBytes defensively copies byte array on construction")
    void shouldDefensivelyCopyOnConstruction() {
        byte[] original = new byte[]{1, 2, 3, 4};
        CryptoBytes cryptoBytes = new CryptoBytes(original);

        // Mutate original array
        original[0] = 99;

        assertThat(cryptoBytes.value()[0]).isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("Assert CryptoBytes defensively copies byte array on access")
    void shouldDefensivelyCopyOnAccess() {
        byte[] original = new byte[]{1, 2, 3, 4};
        CryptoBytes cryptoBytes = new CryptoBytes(original);

        // Mutate array returned from .value()
        byte[] accessed = cryptoBytes.value();
        accessed[0] = 88;

        assertThat(cryptoBytes.value()[0]).isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("Assert CryptoEnvelope remains unaltered when input arrays are modified externally")
    void shouldPreserveEnvelopeImmutability() {
        byte[] ivBytes = new byte[12];
        ivBytes[0] = 7;
        byte[] dekBytes = new byte[32];
        dekBytes[0] = 8;
        byte[] cipherBytes = new byte[32];
        cipherBytes[0] = 9;

        CryptoEnvelope envelope = new CryptoEnvelope(
                EnvelopeVersion.WALLET_ENV_V1,
                new TenantId("tenant-alpha"),
                new OperationId(UUID.randomUUID()),
                new KeyId("kms-key-1"),
                EncryptionAlgorithm.AES_256_GCM,
                new CryptoBytes(ivBytes),
                new CryptoBytes(dekBytes),
                new CryptoBytes(cipherBytes)
        );

        // Mutate caller arrays
        ivBytes[0] = 77;
        dekBytes[0] = 88;
        cipherBytes[0] = 99;

        assertThat(envelope.iv().value()[0]).isEqualTo((byte) 7);
        assertThat(envelope.wrappedDek().value()[0]).isEqualTo((byte) 8);
        assertThat(envelope.ciphertext().value()[0]).isEqualTo((byte) 9);
    }
}
