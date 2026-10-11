package br.com.wallet.decision.composition;

/**
 * Aggregation policy defining how the composer handles unavailable or unverified questions.
 */
public enum CompositionPolicy {
    /**
     * Abort composition and return INCONCLUSIVE if any mandatory or evaluated question is unavailable.
     */
    FAIL_CLOSED,

    /**
     * Require explicit set of mandatory questions; allow non-mandatory questions to be unavailable.
     */
    REQUIRE_MANDATORY_QUESTIONS,

    /**
     * Degrade assessment status to UNVERIFIED_PARTIAL without treating unavailable decisions as false or zero.
     */
    DEGRADE_TO_UNVERIFIED
}
