package br.com.wallet.decision.catalog;

import br.com.wallet.decision.model.DecisionQuestion;
import br.com.wallet.decision.model.SubscriptionClassification;

/**
 * Standard domain question catalog for subscription intelligence (REQ-SUB-010, REQ-SUB-011).
 */
public final class SubscriptionDecisionQuestions {

    private SubscriptionDecisionQuestions() {
    }

    public static final DecisionQuestion<SubscriptionClassification> SUBSCRIPTION_CLASSIFICATION = new DecisionQuestion<>(
            "Q-SUB-001",
            "SUBSCRIPTION_CLASSIFICATION",
            "Semantic category classification of recurring subscription pattern (e.g., ENTERTAINMENT, UTILITY, SAAS)",
            SubscriptionClassification.class
    );
}
