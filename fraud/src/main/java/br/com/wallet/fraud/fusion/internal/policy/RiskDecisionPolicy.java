package br.com.wallet.fraud.fusion.internal.policy;

import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * Evaluates the 4-state graduated fraud decision policy (REQ-FUSION-005, REQ-FUSION-011, I-FUSION-001).
 */
@Component
public class RiskDecisionPolicy {

    public static final double HARD_BLOCK_THRESHOLD = 1.0;
    public static final double RESTRICT_THRESHOLD = 0.85;
    public static final double REVIEW_THRESHOLD = 0.50;

    @NonNull
    public FraudDecision evaluate(double directRisk, double finalRisk) {
        // I-FUSION-001: Direct Rule Primacy Override before math
        if (directRisk >= HARD_BLOCK_THRESHOLD) {
            return FraudDecision.HARD_BLOCK;
        }

        // Graduated decision thresholds
        if (finalRisk >= RESTRICT_THRESHOLD) {
            return FraudDecision.RESTRICT;
        }
        if (finalRisk >= REVIEW_THRESHOLD) {
            return FraudDecision.REVIEW;
        }
        return FraudDecision.ALLOW;
    }

    public boolean shouldTriggerInvestigation(@NonNull FraudDecision decision) {
        return decision == FraudDecision.REVIEW || decision == FraudDecision.RESTRICT;
    }
}
