package br.com.wallet.fraud.fusion.internal.persistence;

/**
 * State machine for durable fusion evaluation jobs (REQ-FUSION-004, REQ-FUSION-009).
 */
public enum FusionJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    RETRY_WAIT,
    FAILED
}
