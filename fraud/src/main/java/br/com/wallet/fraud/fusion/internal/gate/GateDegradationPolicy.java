package br.com.wallet.fraud.fusion.internal.gate;

/**
 * Contextual fallback strategies when the hot state store is unavailable or missing profiles (REQ-FUSION-007).
 */
public enum GateDegradationPolicy {
    FAIL_CLOSED,
    DETERMINISTIC_FALLBACK
}
