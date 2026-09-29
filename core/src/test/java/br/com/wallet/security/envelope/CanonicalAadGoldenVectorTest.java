package br.com.wallet.security.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.2: Canonical AAD Length-Prefixed Serialization & Golden Vector Test (REQ-SEC-023, I-ENV-002)")
class CanonicalAadGoldenVectorTest {

    private static final String PROTOCOL_VERSION = "WALLET-ENV-AAD-V1";

    @Test
    @DisplayName("Assert canonical AAD computes exact deterministic length-prefixed bytes")
    void shouldComputeDeterministicCanonicalAad() {
        UUID opUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        EnvelopeVersion version = EnvelopeVersion.WALLET_ENV_V1;
        TenantId tenantId = new TenantId("tenant-alpha");
        OperationId operationId = new OperationId(opUuid);
        KeyId keyId = new KeyId("key-1");
        EncryptionAlgorithm algorithm = EncryptionAlgorithm.AES_256_GCM;

        byte[] aad = CanonicalAad.compute(version, tenantId, operationId, keyId, algorithm);

        // Verify structure: each field is preceded by 4-byte big-endian length
        ByteBuffer buf = ByteBuffer.wrap(aad);

        // Field 0: Protocol version
        int len0 = buf.getInt();
        byte[] bytes0 = new byte[len0];
        buf.get(bytes0);
        assertThat(new String(bytes0, StandardCharsets.UTF_8)).isEqualTo(PROTOCOL_VERSION);

        // Field 1: Version
        int len1 = buf.getInt();
        byte[] bytes1 = new byte[len1];
        buf.get(bytes1);
        assertThat(new String(bytes1, StandardCharsets.UTF_8)).isEqualTo("WALLET-ENV-V1");

        // Field 2: TenantId
        int len2 = buf.getInt();
        byte[] bytes2 = new byte[len2];
        buf.get(bytes2);
        assertThat(new String(bytes2, StandardCharsets.UTF_8)).isEqualTo("tenant-alpha");

        // Field 3: OperationId
        int len3 = buf.getInt();
        byte[] bytes3 = new byte[len3];
        buf.get(bytes3);
        assertThat(new String(bytes3, StandardCharsets.UTF_8)).isEqualTo(opUuid.toString());

        // Field 4: KeyId
        int len4 = buf.getInt();
        byte[] bytes4 = new byte[len4];
        buf.get(bytes4);
        assertThat(new String(bytes4, StandardCharsets.UTF_8)).isEqualTo("key-1");

        // Field 5: Algorithm
        int len5 = buf.getInt();
        byte[] bytes5 = new byte[len5];
        buf.get(bytes5);
        assertThat(new String(bytes5, StandardCharsets.UTF_8)).isEqualTo("AES_256_GCM");

        assertThat(buf.hasRemaining()).isFalse();
    }

    @Test
    @DisplayName("Assert length-prefixing prevents collision between concatenated fields")
    void shouldPreventBoundaryCollisionAttacks() {
        UUID opUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        EnvelopeVersion version = EnvelopeVersion.WALLET_ENV_V1;
        KeyId keyId = new KeyId("key-1");
        EncryptionAlgorithm algorithm = EncryptionAlgorithm.AES_256_GCM;

        // Scenario: tenant "ab", opUuid vs tenant "a", "b"+opUuid (if delimiter-less or naive string concatenation)
        // With length-prefixing:
        byte[] aad1 = CanonicalAad.compute(version, new TenantId("tenant-alpha"), new OperationId(opUuid), keyId, algorithm);
        byte[] aad2 = CanonicalAad.compute(version, new TenantId("tenant-alph"), new OperationId(opUuid), keyId, algorithm);

        assertThat(aad1).isNotEqualTo(aad2);
    }

    @Test
    @DisplayName("Assert special delimiter characters in identifiers serialize unambiguously")
    void shouldHandleSpecialDelimiterCharactersSafely() {
        UUID opUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        EnvelopeVersion version = EnvelopeVersion.WALLET_ENV_V1;
        // Tenant with colons, pipes, and commas
        TenantId tenantWithDelimiters = new TenantId("tenant:corp|region,zone");
        KeyId keyWithDelimiters = new KeyId("kms/arn:aws:kms:us-east-1:123456789:key|alias");
        EncryptionAlgorithm algorithm = EncryptionAlgorithm.AES_256_GCM;

        byte[] aad = CanonicalAad.compute(version, tenantWithDelimiters, new OperationId(opUuid), keyWithDelimiters, algorithm);

        ByteBuffer buf = ByteBuffer.wrap(aad);
        // Skip protocol + version
        buf.position(4 + PROTOCOL_VERSION.length() + 4 + version.code().length());

        int tenantLen = buf.getInt();
        byte[] tenantBytes = new byte[tenantLen];
        buf.get(tenantBytes);
        assertThat(new String(tenantBytes, StandardCharsets.UTF_8)).isEqualTo("tenant:corp|region,zone");

        int opLen = buf.getInt();
        byte[] opBytes = new byte[opLen];
        buf.get(opBytes);
        assertThat(new String(opBytes, StandardCharsets.UTF_8)).isEqualTo(opUuid.toString());

        int keyLen = buf.getInt();
        byte[] keyBytes = new byte[keyLen];
        buf.get(keyBytes);
        assertThat(new String(keyBytes, StandardCharsets.UTF_8)).isEqualTo("kms/arn:aws:kms:us-east-1:123456789:key|alias");
    }

    @Test
    @DisplayName("Assert CanonicalAad.compute(envelope) produces identical output")
    void shouldProduceIdenticalAadFromEnvelope() {
        CryptoEnvelope envelope = new CryptoEnvelope(
                EnvelopeVersion.WALLET_ENV_V1,
                new TenantId("tenant-1"),
                new OperationId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                new KeyId("key-root"),
                EncryptionAlgorithm.AES_256_GCM,
                new CryptoBytes(new byte[12]),
                new CryptoBytes(new byte[32]),
                new CryptoBytes(new byte[16])
        );

        byte[] aadFromEnv = CanonicalAad.compute(envelope);
        byte[] aadFromFields = CanonicalAad.compute(
                envelope.version(), envelope.tenantId(), envelope.operationId(), envelope.keyId(), envelope.algorithm()
        );

        assertThat(aadFromEnv).isEqualTo(aadFromFields);
    }
}
