package br.com.wallet.security.failure;

import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.TenantId;

/**
 * Thrown when Key Management Service (KMS) operations encounter transient or fatal failures (REQ-SEC-030).
 */
public class KeyManagementUnavailableException extends RuntimeException {

    private final TenantId tenantId;
    private final KeyId keyId;

    public KeyManagementUnavailableException(String message, TenantId tenantId, KeyId keyId) {
        super(message);
        this.tenantId = tenantId;
        this.keyId = keyId;
    }

    public KeyManagementUnavailableException(String message, TenantId tenantId, KeyId keyId, Throwable cause) {
        super(message, cause);
        this.tenantId = tenantId;
        this.keyId = keyId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public KeyId getKeyId() {
        return keyId;
    }
}
