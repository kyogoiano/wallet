package br.com.wallet.security.envelope;

import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-10.15: GCM IV CSPRNG Uniqueness & Multi-Thread Concurrency Test (I-ENV-006)")
class GcmIvUniquenessTest {

    @Test
    @DisplayName("Assert 100,000 concurrently generated IVs under same key yield zero collisions (I-ENV-006)")
    void shouldGuaranteeIvUniquenessUnderConcurrency() throws Exception {
        int threadCount = 8;
        int ivsPerThread = 12_500; // Total 100,000 IVs
        int totalIvs = threadCount * ivsPerThread;

        Set<ByteBuffer> ivSet = ConcurrentHashMap.newKeySet(totalIvs);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AesGcmEnvelopeEncryptor encryptor = new AesGcmEnvelopeEncryptor();

        TenantId tenantId = new TenantId("tenant-perf");
        KeyId keyId = new KeyId("key-primary");
        byte[] payload = "{}".getBytes();

        byte[] rawKey = new byte[32];
        new SecureRandom().nextBytes(rawKey);
        GeneratedDataKey dataKey = new GeneratedDataKey(new SensitiveKeyMaterial(rawKey), new CryptoBytes(new byte[16]));

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < ivsPerThread; i++) {
                        CryptoEnvelope envelope = encryptor.encrypt(
                                payload, tenantId, new OperationId(UUID.randomUUID()), keyId, dataKey
                        );
                        assertThat(envelope.iv().length()).isEqualTo(12);
                        ivSet.add(ByteBuffer.wrap(envelope.iv().value()));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Assert zero collisions across all 100,000 IVs
        assertThat(ivSet).hasSize(totalIvs);
    }
}
