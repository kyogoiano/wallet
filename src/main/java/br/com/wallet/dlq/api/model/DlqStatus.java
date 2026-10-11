package br.com.wallet.dlq.api.model;

/**
 * State machine representation of a DLQ operation lifecycle (REQ-TDLQ-004, I-TDLQ-004).
 */
public enum DlqStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    EXHAUSTED,
    QUARANTINED,
    DISCARDED;

    /**
     * Determines whether this status is eligible for background automated reprocess attempts.
     */
    public boolean isAutomatedRetryEligible() {
        return this == PENDING || this == FAILED;
    }

    /**
     * Determines whether this status is terminal (cannot be picked up automatically).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == EXHAUSTED || this == QUARANTINED || this == DISCARDED;
    }

    /**
     * Validates whether a transition from this state to the target state is permissible.
     */
    public boolean canTransitionTo(final DlqStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PROCESSING;
            case PROCESSING -> target == COMPLETED || target == FAILED || target == EXHAUSTED || target == QUARANTINED;
            case FAILED -> target == PENDING || target == EXHAUSTED;
            case EXHAUSTED, QUARANTINED -> target == PENDING || target == DISCARDED;
            case COMPLETED, DISCARDED -> false;
        };
    }
}
