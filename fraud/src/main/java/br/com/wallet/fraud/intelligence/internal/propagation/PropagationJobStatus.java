package br.com.wallet.fraud.intelligence.internal.propagation;

/**
 * Lifecycle states for hand-rolled durable propagation jobs.
 */
public enum PropagationJobStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    COMPLETED,
    FAILED
}
