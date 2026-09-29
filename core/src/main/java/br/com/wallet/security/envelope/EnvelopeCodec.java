package br.com.wallet.security.envelope;

import br.com.wallet.security.failure.MalformedEnvelopeException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * High-performance binary codec for serializing and deserializing {@link CryptoEnvelope} instances (REQ-SEC-020, I-ENV-001).
 */
public final class EnvelopeCodec {

    public static final int MAGIC = 0x454E5631; // "ENV1"

    private EnvelopeCodec() {
        // Prevent instantiation
    }

    public static byte[] encode(CryptoEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");

        byte[] verBytes = envelope.version().code().getBytes(StandardCharsets.UTF_8);
        byte[] tenantBytes = envelope.tenantId().value().getBytes(StandardCharsets.UTF_8);
        UUID opId = envelope.operationId().value();
        byte[] keyBytes = envelope.keyId().value().getBytes(StandardCharsets.UTF_8);
        byte[] algBytes = envelope.algorithm().code().getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = envelope.iv().value();
        byte[] wrappedBytes = envelope.wrappedDek().value();
        byte[] cipherBytes = envelope.ciphertext().value();

        int totalLen = 4 // magic
                + 2 + verBytes.length
                + 2 + tenantBytes.length
                + 16 // UUID (MSB + LSB)
                + 2 + keyBytes.length
                + 2 + algBytes.length
                + 2 + ivBytes.length
                + 4 + wrappedBytes.length
                + 4 + cipherBytes.length;

        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.putInt(MAGIC);

        buf.putShort((short) verBytes.length);
        buf.put(verBytes);

        buf.putShort((short) tenantBytes.length);
        buf.put(tenantBytes);

        buf.putLong(opId.getMostSignificantBits());
        buf.putLong(opId.getLeastSignificantBits());

        buf.putShort((short) keyBytes.length);
        buf.put(keyBytes);

        buf.putShort((short) algBytes.length);
        buf.put(algBytes);

        buf.putShort((short) ivBytes.length);
        buf.put(ivBytes);

        buf.putInt(wrappedBytes.length);
        buf.put(wrappedBytes);

        buf.putInt(cipherBytes.length);
        buf.put(cipherBytes);

        return buf.array();
    }

    public static CryptoEnvelope decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length < 4 + 2 + 2 + 16 + 2 + 2 + 2 + 4 + 4) {
            throw new MalformedEnvelopeException("Byte array too short to contain CryptoEnvelope prefix: " + bytes.length);
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        int magic = buf.getInt();
        if (magic != MAGIC) {
            throw new MalformedEnvelopeException("Invalid envelope magic: 0x" + Integer.toHexString(magic));
        }

        short verLen = buf.getShort();
        byte[] verBytes = new byte[verLen];
        buf.get(verBytes);
        EnvelopeVersion version = EnvelopeVersion.fromCode(new String(verBytes, StandardCharsets.UTF_8));

        short tenantLen = buf.getShort();
        byte[] tenantBytes = new byte[tenantLen];
        buf.get(tenantBytes);
        TenantId tenantId = new TenantId(new String(tenantBytes, StandardCharsets.UTF_8));

        long msb = buf.getLong();
        long lsb = buf.getLong();
        OperationId operationId = new OperationId(new UUID(msb, lsb));

        short keyLen = buf.getShort();
        byte[] keyBytes = new byte[keyLen];
        buf.get(keyBytes);
        KeyId keyId = new KeyId(new String(keyBytes, StandardCharsets.UTF_8));

        short algLen = buf.getShort();
        byte[] algBytes = new byte[algLen];
        buf.get(algBytes);
        EncryptionAlgorithm algorithm = EncryptionAlgorithm.fromCode(new String(algBytes, StandardCharsets.UTF_8));

        short ivLen = buf.getShort();
        byte[] ivBytes = new byte[ivLen];
        buf.get(ivBytes);

        int wrappedLen = buf.getInt();
        byte[] wrappedBytes = new byte[wrappedLen];
        buf.get(wrappedBytes);

        int cipherLen = buf.getInt();
        byte[] cipherBytes = new byte[cipherLen];
        buf.get(cipherBytes);

        return new CryptoEnvelope(
                version,
                tenantId,
                operationId,
                keyId,
                algorithm,
                new CryptoBytes(ivBytes),
                new CryptoBytes(wrappedBytes),
                new CryptoBytes(cipherBytes)
        );
    }
}
