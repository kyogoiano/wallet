package br.com.wallet.security.replay;

/**
 * Rejection reason taxonomy for nonce admission checks (REQ-SEC-028, I-ENV-004).
 */
public enum ReplayRejectionReason {
    /**
     * Nonce has already been committed to durable journal storage.
     */
    DUPLICATE_NONCE,

    /**
     * Nonce is actively held under a concurrent unexpired reservation lease.
     */
    RESERVATION_IN_PROGRESS,

    /**
     * Nonce does not satisfy formatting or entropy requirements.
     */
    INVALID_NONCE
}
