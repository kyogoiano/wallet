package br.com.wallet.security.envelope;

import br.com.wallet.security.keymanagement.GeneratedDataKey;

/**
 * High-performance envelope encryption engine interface for edge ingress (REQ-SEC-020, I-ENV-001, I-ENV-005).
 */
public interface EnvelopeEncryptor {

    /**
     * Encrypts plaintext command bytes into a strongly bound {@link CryptoEnvelope} using AES-256-GCM.
     *
     * @param plaintext The raw financial command payload bytes.
     * @param tenantId The authenticated tenant identifier.
     * @param operationId The unique operation / idempotency identifier.
     * @param keyId The KMS KEK identifier.
     * @param dataKey The active data key holding the plaintext and wrapped DEK.
     * @return The immutable {@link CryptoEnvelope}.
     */
    CryptoEnvelope encrypt(
            byte[] plaintext,
            TenantId tenantId,
            OperationId operationId,
            KeyId keyId,
            GeneratedDataKey dataKey
    );
}
