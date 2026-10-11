package br.com.wallet.security.keymanagement;

import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.TenantId;

/**
 * Service Provider Interface (SPI) for provider-neutral Key Management Service (KMS) operations (REQ-SEC-024, I-SEC-011).
 *
 * <p>Concrete implementations (AWS KMS, HashiCorp Vault, Local Appliance, or Cached wrappers)
 * reside strictly outside the pure domain contracts package.
 */
public interface KeyManagementClient {

    /**
     * Generates a new 256-bit data encryption key (DEK) bound to the tenant's KMS context.
     *
     * @param tenantId The authenticated tenant identifier.
     * @param keyId The KMS KEK identifier.
     * @param context The cryptographic encryption context binding the operation.
     * @return An AutoCloseable {@link GeneratedDataKey} holding the plaintext and wrapped DEKs.
     */
    GeneratedDataKey generateDataKey(TenantId tenantId, KeyId keyId, KeyContext context);

    /**
     * Decrypts an existing KMS-wrapped DEK using the authenticated tenant's context.
     *
     * @param tenantId The authenticated tenant identifier.
     * @param keyId The KMS KEK identifier.
     * @param wrappedDek The opaque wrapped DEK ciphertext.
     * @param context The cryptographic encryption context that was used during generation.
     * @return An AutoCloseable {@link SensitiveKeyMaterial} holding the unwrapped plaintext DEK.
     */
    SensitiveKeyMaterial decryptDataKey(TenantId tenantId, KeyId keyId, CryptoBytes wrappedDek, KeyContext context);
}
