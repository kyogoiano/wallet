package br.com.wallet.security.failure;

/**
 * Thrown when cryptographic envelope deserialization encounters corrupt or truncated bytes (REQ-SEC-030).
 */
public class MalformedEnvelopeException extends RuntimeException {

    public MalformedEnvelopeException(String message) {
        super(message);
    }

    public MalformedEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
