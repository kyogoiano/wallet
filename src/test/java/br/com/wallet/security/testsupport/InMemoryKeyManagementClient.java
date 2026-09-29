package br.com.wallet.security.testsupport;

import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.failure.KeyManagementUnavailableException;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.KeyContext;
import br.com.wallet.security.keymanagement.KeyManagementClient;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic in-memory KMS implementation strictly scoped for automated unit testing (REQ-SEC-024, I-SEC-015).
 */
public class InMemoryKeyManagementClient implements KeyManagementClient {

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, byte[]> wrappedToPlaintextMap = new ConcurrentHashMap<>();
    private final AtomicInteger generateCount = new AtomicInteger(0);
    private final AtomicInteger decryptCount = new AtomicInteger(0);
    private final AtomicBoolean simulateFailure = new AtomicBoolean(false);

    public void setSimulateFailure(boolean fail) {
        this.simulateFailure.set(fail);
    }

    public int getGenerateCount() {
        return generateCount.get();
    }

    public int getDecryptCount() {
        return decryptCount.get();
    }

    @Override
    public GeneratedDataKey generateDataKey(TenantId tenantId, KeyId keyId, KeyContext context) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (simulateFailure.get()) {
            throw new KeyManagementUnavailableException("Simulated KMS failure", tenantId, keyId);
        }

        generateCount.incrementAndGet();

        byte[] rawPlaintext = new byte[32];
        secureRandom.nextBytes(rawPlaintext);

        // Deterministic wrapped representation binding tenantId
        String wrappedStr = "WRAPPED:" + tenantId.value() + ":" + keyId.value() + ":" + java.util.UUID.randomUUID();
        byte[] wrappedBytes = wrappedStr.getBytes(StandardCharsets.UTF_8);

        wrappedToPlaintextMap.put(Arrays.toString(wrappedBytes), rawPlaintext.clone());

        return new GeneratedDataKey(
                new SensitiveKeyMaterial(rawPlaintext),
                new CryptoBytes(wrappedBytes)
        );
    }

    @Override
    public SensitiveKeyMaterial decryptDataKey(TenantId tenantId, KeyId keyId, CryptoBytes wrappedDek, KeyContext context) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (simulateFailure.get()) {
            throw new KeyManagementUnavailableException("Simulated KMS failure", tenantId, keyId);
        }

        decryptCount.incrementAndGet();

        // Validate context matches tenant
        if (!context.tenantId().equals(tenantId)) {
            throw new KeyManagementUnavailableException("Tenant mismatch in KMS context", tenantId, keyId);
        }

        byte[] plaintext = wrappedToPlaintextMap.get(Arrays.toString(wrappedDek.value()));
        if (plaintext == null) {
            // For testing simulated invalid wrapped DEKs
            throw new KeyManagementUnavailableException("Unknown wrapped DEK", tenantId, keyId);
        }

        return new SensitiveKeyMaterial(plaintext);
    }
}
