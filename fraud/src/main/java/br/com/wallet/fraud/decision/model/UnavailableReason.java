package br.com.wallet.fraud.decision.model;

/**
 * Diagnostic reason explaining why a decision could not be fulfilled.
 */
public enum UnavailableReason {
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    RATE_LIMITED,
    PARSE_FAILURE,
    INSUFFICIENT_EVIDENCE
}
