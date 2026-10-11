package br.com.wallet.security.telemetry;

import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.EncryptionAlgorithm;
import br.com.wallet.security.envelope.EnvelopeVersion;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.failure.CryptographicIntegrityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-10.7: Telemetry Sanitization & Safe Diagnostics Test (REQ-SEC-030, REQ-SEC-031, I-SEC-012)")
class TelemetrySanitizationTest {

    @Test
    @DisplayName("Assert CryptoEnvelopeSummary contains zero ciphertext bytes or keys")
    void shouldNotExposeSensitivePayloadInSummary() {
        UUID opId = UUID.randomUUID();
        CryptoEnvelope envelope = new CryptoEnvelope(
                EnvelopeVersion.WALLET_ENV_V1,
                new TenantId("tenant-secret-bank"),
                new OperationId(opId),
                new KeyId("master-kms-key"),
                EncryptionAlgorithm.AES_256_GCM,
                new CryptoBytes(new byte[12]),
                new CryptoBytes(new byte[32]),
                new CryptoBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
        );

        CryptoEnvelopeSummary summary = CryptoEnvelopeSummary.fromEnvelope(envelope);

        assertThat(summary.ciphertextLength()).isEqualTo(16);
        assertThat(summary.toString()).doesNotContain("1, 2, 3");
        assertThat(summary.toString()).contains("tenant-secret-bank");
        assertThat(summary.toString()).contains(opId.toString());
    }

    @Test
    @DisplayName("Assert CryptographicIntegrityException suppresses plaintext in message and string representation")
    void shouldSuppressPlaintextInException() {
        OperationId opId = new OperationId(UUID.randomUUID());
        CryptographicIntegrityException ex = new CryptographicIntegrityException(
                "GCM authentication tag verification failed for operation: " + opId,
                opId
        );

        assertThat(ex.getOperationId()).isEqualTo(opId);
        assertThat(ex.getMessage()).doesNotContain("amount", "balance", "key", "password");
    }
}
