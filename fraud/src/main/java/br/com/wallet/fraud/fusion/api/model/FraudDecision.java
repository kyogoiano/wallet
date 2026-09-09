package br.com.wallet.fraud.fusion.api.model;

/**
 * 4-state fraud decision representing the graduated risk policy (REQ-FUSION-011).
 */
public enum FraudDecision {
    /**
     * Final risk < 0.50: Transaction or entity permitted unconditionally.
     */
    ALLOW,

    /**
     * 0.50 <= Final risk < 0.85: Permitted with asynchronous human/agent investigation dossier.
     */
    REVIEW,

    /**
     * Final risk >= 0.85: Temporary outgoing monetary restriction applied, investigation queued.
     */
    RESTRICT,

    /**
     * Direct hard rule violated (directRisk >= 1.0): Immediate account hard-block.
     */
    HARD_BLOCK
}
