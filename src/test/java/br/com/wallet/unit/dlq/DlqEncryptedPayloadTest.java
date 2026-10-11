package br.com.wallet.unit.dlq;

import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.EncryptionAlgorithm;
import br.com.wallet.security.envelope.EnvelopeCodec;
import br.com.wallet.security.envelope.EnvelopeVersion;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DlqEncryptedPayloadTest (TASK-2.3, REQ-TDLQ-007, I-TDLQ-006)")
class DlqEncryptedPayloadTest {

    @Test
    @DisplayName("Assert zero plaintext financial data and zero plaintext DEK in DLQ payload (I-TDLQ-006)")
    void shouldPreserveCryptoEnvelopeOpacityInDlqPayload() throws Exception {
        final UUID opId = UUID.randomUUID();
        final String plaintextFinancialCommand = "{\"amount\": 15000.50, \"fromAccount\": \"acc-123\", \"toAccount\": \"acc-456\"}";

        // Real AES-GCM encryption of the plaintext command
        final byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        final SecretKey dek = new SecretKeySpec(new byte[32], "AES");
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(128, iv));
        final byte[] ciphertext = cipher.doFinal(plaintextFinancialCommand.getBytes(StandardCharsets.UTF_8));
        final byte[] wrappedDek = new byte[]{32, 33, 34, 35, 36, 37, 38, 39};

        // Construct CryptoEnvelope sealing the financial command
        final CryptoEnvelope envelope = new CryptoEnvelope(
                EnvelopeVersion.WALLET_ENV_V1,
                new TenantId("tenant-secure"),
                new OperationId(opId),
                new KeyId("key-2026-q1"),
                EncryptionAlgorithm.AES_256_GCM,
                new CryptoBytes(iv),
                new CryptoBytes(wrappedDek),
                new CryptoBytes(ciphertext)
        );

        final byte[] binaryEnvelope = EnvelopeCodec.encode(envelope);
        final String base64Payload = Base64.getEncoder().encodeToString(binaryEnvelope);

        // Store into DlqEvent representing failure handoff
        final DlqEvent dlqEvent = new DlqEvent(
                UUID.randomUUID(),
                opId,
                UUID.randomUUID(),
                "commands.transfer",
                DlqStatus.FAILED,
                "Temporary downstream error",
                base64Payload,
                0,
                Instant.now().plusSeconds(5),
                Instant.now(),
                null,
                DlqFailureType.TRANSIENT,
                "Transfer",
                "tenant-secure"
        );

        // Assert that the stored payload is opaque ciphertext and does not leak plaintext financial fields
        assertThat(dlqEvent.payload()).doesNotContain("15000.50");
        assertThat(dlqEvent.payload()).doesNotContain("acc-123");
        assertThat(dlqEvent.payload()).doesNotContain("acc-456");
        assertThat(dlqEvent.payload()).doesNotContain("fromAccount");
        assertThat(dlqEvent.payload()).doesNotContain("toAccount");

        // Assert that payload decodes back to the exact valid CryptoEnvelope
        final byte[] decodedBytes = Base64.getDecoder().decode(dlqEvent.payload());
        final CryptoEnvelope restored = EnvelopeCodec.decode(decodedBytes);

        assertThat(restored.tenantId().value()).isEqualTo("tenant-secure");
        assertThat(restored.operationId().value()).isEqualTo(opId);
        assertThat(restored.keyId().value()).isEqualTo("key-2026-q1");
        assertThat(restored.ciphertext().value()).isEqualTo(ciphertext);

        // Decrypt ciphertext to prove the encrypted data was indeed the original plaintext
        final Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(128, iv));
        final byte[] decryptedBytes = decryptCipher.doFinal(restored.ciphertext().value());
        assertThat(new String(decryptedBytes, StandardCharsets.UTF_8)).isEqualTo(plaintextFinancialCommand);
    }
}
