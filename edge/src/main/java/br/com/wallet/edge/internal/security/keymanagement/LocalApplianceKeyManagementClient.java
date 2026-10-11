package br.com.wallet.edge.internal.security.keymanagement;

import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.failure.KeyManagementUnavailableException;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.KeyContext;
import br.com.wallet.security.keymanagement.KeyManagementClient;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * On-premises local appliance KMS implementation providing local cryptographic master KEK management
 * with JEP 538 PEM serialization support (REQ-SEC-025, JEP 538, I-ENV-002).
 */
public final class LocalApplianceKeyManagementClient implements KeyManagementClient {

    private static final String PEM_HEADER = "-----BEGIN AES KEY-----";
    private static final String PEM_FOOTER = "-----END AES KEY-----";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int KEY_LENGTH_BYTES = 32;

    private final byte[] masterKekBytes;
    private final Instant creationInstant;
    private final SecureRandom secureRandom;

    public LocalApplianceKeyManagementClient() {
        this.secureRandom = new SecureRandom();
        this.masterKekBytes = new byte[KEY_LENGTH_BYTES];
        this.secureRandom.nextBytes(this.masterKekBytes);
        this.creationInstant = Instant.now();
    }

    public LocalApplianceKeyManagementClient(byte[] masterKekBytes) {
        Objects.requireNonNull(masterKekBytes, "masterKekBytes must not be null");
        if (masterKekBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("masterKekBytes must be exactly 32 bytes (256 bits), found: " + masterKekBytes.length);
        }
        this.secureRandom = new SecureRandom();
        this.masterKekBytes = masterKekBytes.clone();
        this.creationInstant = Instant.now();
    }

    public Instant getCreationInstant() {
        return creationInstant;
    }

    public String exportMasterKekPem() {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(masterKekBytes);
        return PEM_HEADER + "\n" + base64 + "\n" + PEM_FOOTER + "\n";
    }

    public static LocalApplianceKeyManagementClient fromPem(String pem) {
        Objects.requireNonNull(pem, "pem must not be null");
        String trimmed = pem.trim();
        if (!trimmed.startsWith(PEM_HEADER) || !trimmed.endsWith(PEM_FOOTER)) {
            throw new IllegalArgumentException("Invalid PEM format: missing header or footer");
        }

        String base64Body = trimmed
                .substring(PEM_HEADER.length(), trimmed.length() - PEM_FOOTER.length())
                .replaceAll("\\s+", "");

        try {
            byte[] decoded = Base64.getDecoder().decode(base64Body);
            return new LocalApplianceKeyManagementClient(decoded);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid Base64 payload in PEM", ex);
        }
    }

    @Override
    public GeneratedDataKey generateDataKey(TenantId tenantId, KeyId keyId, KeyContext context) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        byte[] rawPlaintextDek = new byte[KEY_LENGTH_BYTES];
        secureRandom.nextBytes(rawPlaintextDek);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        byte[] aad = context.tenantId().value().getBytes(StandardCharsets.UTF_8);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec kekSpec = new SecretKeySpec(masterKekBytes, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.ENCRYPT_MODE, kekSpec, parameterSpec);
            cipher.updateAAD(aad);
            byte[] cipherWithTag = cipher.doFinal(rawPlaintextDek);

            ByteBuffer wrappedBuf = ByteBuffer.allocate(GCM_IV_LENGTH_BYTES + cipherWithTag.length);
            wrappedBuf.put(iv);
            wrappedBuf.put(cipherWithTag);

            return new GeneratedDataKey(
                    new SensitiveKeyMaterial(rawPlaintextDek),
                    new CryptoBytes(wrappedBuf.array())
            );
        } catch (GeneralSecurityException ex) {
            throw new KeyManagementUnavailableException("Failed to wrap DEK under appliance master KEK", tenantId, keyId, ex);
        }
    }

    @Override
    public SensitiveKeyMaterial decryptDataKey(TenantId tenantId, KeyId keyId, CryptoBytes wrappedDek, KeyContext context) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (!context.tenantId().equals(tenantId)) {
            throw new KeyManagementUnavailableException("Tenant mismatch in KMS context: " + context.tenantId() + " vs " + tenantId, tenantId, keyId);
        }

        byte[] wrappedBytes = wrappedDek.value();
        if (wrappedBytes.length < GCM_IV_LENGTH_BYTES + KEY_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw new KeyManagementUnavailableException("Wrapped DEK too short", tenantId, keyId);
        }

        ByteBuffer wrappedBuf = ByteBuffer.wrap(wrappedBytes);
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        wrappedBuf.get(iv);

        byte[] cipherWithTag = new byte[wrappedBuf.remaining()];
        wrappedBuf.get(cipherWithTag);

        byte[] aad = context.tenantId().value().getBytes(StandardCharsets.UTF_8);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec kekSpec = new SecretKeySpec(masterKekBytes, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.DECRYPT_MODE, kekSpec, parameterSpec);
            cipher.updateAAD(aad);
            byte[] plaintext = cipher.doFinal(cipherWithTag);

            return new SensitiveKeyMaterial(plaintext);
        } catch (GeneralSecurityException ex) {
            throw new KeyManagementUnavailableException("Failed to unwrap DEK under tenant context", tenantId, keyId, ex);
        }
    }
}
