package br.com.wallet.security.failure;

/**
 * Diagnostic failure classification for cryptographic and perimeter security operations (REQ-SEC-030, I-SEC-012).
 */
public enum SecurityFailureCategory {
    /**
     * Authentication tag mismatch, altered ciphertext, or tampered AAD.
     */
    CRYPTOGRAPHIC_INTEGRITY_VIOLATION,

    /**
     * KMS provider is unreachable, timed out, or ratelimited.
     */
    KEY_MANAGEMENT_UNAVAILABLE,

    /**
     * Replay attempt detected with duplicate nonce.
     */
    REPLAY_DETECTED,

    /**
     * Cryptographic envelope binary or JSON serialization is corrupted or invalid.
     */
    MALFORMED_ENVELOPE,

    /**
     * Tenant mismatch or unauthorized tenant context.
     */
    UNAUTHORIZED_TENANT
}
