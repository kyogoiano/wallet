package br.com.wallet.security.benchmark;

import br.com.wallet.security.envelope.AesGcmEnvelopeDecryptor;
import br.com.wallet.security.envelope.AesGcmEnvelopeEncryptor;
import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AES-256-GCM Benchmark Profile & Runtime Security Inspection (I-ENV-005, TASK-10.17)")
class AesGcmBenchmarkTest {

    private final AesGcmEnvelopeEncryptor encryptor = new AesGcmEnvelopeEncryptor();
    private final AesGcmEnvelopeDecryptor decryptor = new AesGcmEnvelopeDecryptor();

    @Test
    @DisplayName("Verify unlimited crypto policy and JDK security environment (TASK-10.17)")
    void verifyRuntimeSecurityProperties() throws NoSuchAlgorithmException {
        int maxKeyLength = Cipher.getMaxAllowedKeyLength("AES");
        assertThat(maxKeyLength)
                .as("AES key length must support 256-bit encryption under unlimited crypto policy")
                .isGreaterThanOrEqualTo(256);

        String cryptoPolicy = Security.getProperty("crypto.policy");
        if (cryptoPolicy != null) {
            assertThat(cryptoPolicy).isEqualToIgnoringCase("unlimited");
        }

        String keyLimits = Security.getProperty("jdk.tls.keyLimits");
        System.out.println("Runtime Security Property jdk.tls.keyLimits: " + keyLimits);
    }

    @Test
    @DisplayName("I-ENV-005: Profile AES-256-GCM cipher across 256B, 1KB, 2KB, 8KB, 64KB payloads")
    void profileAesGcmAcrossPayloadSizes() {
        int[] payloadSizes = {256, 1024, 2048, 8192, 65536};
        int warmupIterations = 5_000;
        int measureIterations = 5_000;

        TenantId tenantId = new TenantId("bench-tenant");
        KeyId keyId = new KeyId("key-bench-01");
        byte[] rawKey = new byte[32];
        new Random(42).nextBytes(rawKey);

        for (int size : payloadSizes) {
            byte[] payload = new byte[size];
            new Random(1337 + size).nextBytes(payload);

            OperationId opId = new OperationId(UUID.randomUUID());

            // Warmup JIT / AES-NI
            for (int i = 0; i < warmupIterations; i++) {
                try (var dek = new GeneratedDataKey(new SensitiveKeyMaterial(rawKey), new CryptoBytes(new byte[16]))) {
                    CryptoEnvelope envelope = encryptor.encrypt(payload, tenantId, opId, keyId, dek);
                    byte[] decrypted = decryptor.decrypt(envelope, dek.plaintextDek());
                    if (decrypted.length != size) {
                        throw new IllegalStateException("Decryption verification failed");
                    }
                }
            }

            // Measurement
            long[] encryptNanos = new long[measureIterations];
            long[] decryptNanos = new long[measureIterations];

            for (int i = 0; i < measureIterations; i++) {
                try (var dek = new GeneratedDataKey(new SensitiveKeyMaterial(rawKey), new CryptoBytes(new byte[16]))) {
                    long startEnc = System.nanoTime();
                    CryptoEnvelope envelope = encryptor.encrypt(payload, tenantId, opId, keyId, dek);
                    encryptNanos[i] = System.nanoTime() - startEnc;

                    long startDec = System.nanoTime();
                    byte[] decrypted = decryptor.decrypt(envelope, dek.plaintextDek());
                    decryptNanos[i] = System.nanoTime() - startDec;

                    assertThat(decrypted).isEqualTo(payload);
                }
            }

            Arrays.sort(encryptNanos);
            Arrays.sort(decryptNanos);

            long encP50 = encryptNanos[(int) (measureIterations * 0.50)] / 1_000; // microseconds
            long encP95 = encryptNanos[(int) (measureIterations * 0.95)] / 1_000;
            long encP99 = encryptNanos[(int) (measureIterations * 0.99)] / 1_000;

            long decP50 = decryptNanos[(int) (measureIterations * 0.50)] / 1_000;
            long decP95 = decryptNanos[(int) (measureIterations * 0.95)] / 1_000;
            long decP99 = decryptNanos[(int) (measureIterations * 0.99)] / 1_000;

            System.out.printf("[BENCHMARK] Payload %6d B | Encrypt P50: %3dµs, P95: %3dµs, P99: %3dµs | Decrypt P50: %3dµs, P95: %3dµs, P99: %3dµs%n",
                    size, encP50, encP95, encP99, decP50, decP95, decP99);

            // Assert baseline correctness & non-degradation
            assertThat(encP50).isLessThan(500); // 500µs generous bound for CI
            assertThat(decP50).isLessThan(500);
        }
    }
}
