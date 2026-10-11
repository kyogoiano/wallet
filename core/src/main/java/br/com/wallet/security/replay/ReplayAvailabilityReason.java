package br.com.wallet.security.replay;

/**
 * Storage failure taxonomy when nonce admission cannot be verified (REQ-SEC-028, I-ENV-004).
 */
public enum ReplayAvailabilityReason {
    /**
     * Storage cluster (Dragonfly/Redis) is unreachable or disconnected.
     */
    STORAGE_UNAVAILABLE,

    /**
     * Storage operation timed out.
     */
    TIMEOUT,

    /**
     * Circuit breaker is open due to prior repeated storage failures.
     */
    CIRCUIT_OPEN
}
