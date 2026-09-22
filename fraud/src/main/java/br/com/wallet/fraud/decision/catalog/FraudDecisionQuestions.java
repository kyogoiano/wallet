package br.com.wallet.fraud.decision.catalog;

import br.com.wallet.fraud.decision.model.BooleanDecision;
import br.com.wallet.fraud.decision.model.DecisionQuestion;
import br.com.wallet.fraud.decision.model.ScoreDecision;
import br.com.wallet.fraud.decision.model.TextDecision;

import java.util.List;

/**
 * Standard domain catalog of typed fraud questions (REQ-TYPED-003).
 */
public final class FraudDecisionQuestions {

    private FraudDecisionQuestions() {
    }

    public static final DecisionQuestion<BooleanDecision> BEHAVIOR_ANOMALY = new DecisionQuestion<>(
        "Q-FRAUD-001",
        "BEHAVIOR_ANOMALY",
        "Is current transaction pattern anomalous compared to the account's 30-day baseline?",
        BooleanDecision.class
    );

    public static final DecisionQuestion<BooleanDecision> SUSPECTED_MULE_RING = new DecisionQuestion<>(
        "Q-FRAUD-002",
        "SUSPECTED_MULE_RING",
        "Does transaction graph topology and counterparty interaction indicate mule account orchestration?",
        BooleanDecision.class
    );

    public static final DecisionQuestion<ScoreDecision> ANOMALOUS_CASH_OUT = new DecisionQuestion<>(
        "Q-FRAUD-003",
        "ANOMALOUS_CASH_OUT",
        "Quantify rapid cash-out dissipation risk in range [0.00, 1.00]",
        ScoreDecision.class
    );

    public static final DecisionQuestion<TextDecision> INVESTIGATION_SUMMARY = new DecisionQuestion<>(
        "Q-FRAUD-004",
        "INVESTIGATION_SUMMARY",
        "Synthesized human-readable investigation narrative grounded in transactional facts",
        TextDecision.class
    );

    public static List<DecisionQuestion<?>> allStandardQuestions() {
        return List.of(BEHAVIOR_ANOMALY, SUSPECTED_MULE_RING, ANOMALOUS_CASH_OUT, INVESTIGATION_SUMMARY);
    }
}
