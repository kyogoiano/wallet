package br.com.wallet.edge.api;

/**
 * State machine for Reactive Edge readiness lifecycle.
 * Invariant I-EDGE-004: Edge nodes must report OUT_OF_SERVICE until recovery scan is complete.
 */
public enum EdgeReadinessState {
    INITIALIZING,
    RECOVERING,
    READY,
    DEGRADED
}
