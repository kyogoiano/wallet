package br.com.wallet.security.envelope;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Length-prefixed canonical Additional Authenticated Data (AAD) engine (REQ-SEC-023, I-ENV-002).
 *
 * <p>Produces an unambiguous binary representation binding all cryptographic metadata
 * to the AES-GCM ciphertext without relying on delimiter characters.
 */
public final class CanonicalAad {

    public static final String PROTOCOL_VERSION = "WALLET-ENV-AAD-V1";

    private CanonicalAad() {
        // Prevent instantiation
    }

    /**
     * Computes the canonical AAD from an existing {@link CryptoEnvelope}.
     */
    public static byte[] compute(CryptoEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        return compute(
                envelope.version(),
                envelope.tenantId(),
                envelope.operationId(),
                envelope.keyId(),
                envelope.algorithm()
        );
    }

    /**
     * Computes the canonical length-prefixed AAD bytes from the constituent envelope metadata.
     */
    public static byte[] compute(
            EnvelopeVersion version,
            TenantId tenantId,
            OperationId operationId,
            KeyId keyId,
            EncryptionAlgorithm algorithm
    ) {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");

        byte[] protoBytes = PROTOCOL_VERSION.getBytes(StandardCharsets.UTF_8);
        byte[] verBytes = version.code().getBytes(StandardCharsets.UTF_8);
        byte[] tenantBytes = tenantId.value().getBytes(StandardCharsets.UTF_8);
        byte[] opBytes = operationId.value().toString().getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = keyId.value().getBytes(StandardCharsets.UTF_8);
        byte[] algBytes = algorithm.code().getBytes(StandardCharsets.UTF_8);

        int totalLen = 4 + protoBytes.length
                + 4 + verBytes.length
                + 4 + tenantBytes.length
                + 4 + opBytes.length
                + 4 + keyBytes.length
                + 4 + algBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalLen);
        writeField(buffer, protoBytes);
        writeField(buffer, verBytes);
        writeField(buffer, tenantBytes);
        writeField(buffer, opBytes);
        writeField(buffer, keyBytes);
        writeField(buffer, algBytes);

        return buffer.array();
    }

    private static void writeField(ByteBuffer buffer, byte[] bytes) {
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }
}
