package br.com.wallet.security.failure;

import br.com.wallet.security.envelope.OperationId;

/**
 * Thrown when AES-GCM authentication tag verification fails or AAD mismatch is detected (REQ-SEC-030, I-ENV-002, I-SEC-012).
 *
 * <p>Never emits decrypted plaintext or key bytes in the exception message.
 */
public class CryptographicIntegrityException extends RuntimeException {

    private final OperationId operationId;

    public CryptographicIntegrityException(String message, OperationId operationId) {
        super(message);
        this.operationId = operationId;
    }

    public CryptographicIntegrityException(String message, OperationId operationId, Throwable cause) {
        super(message, cause);
        this.operationId = operationId;
    }

    public OperationId getOperationId() {
        return operationId;
    }
}
