package br.com.wallet.fraud.decision.catalog;

import br.com.wallet.fraud.decision.model.BooleanDecision;
import br.com.wallet.fraud.decision.model.DecisionQuestion;
import br.com.wallet.fraud.decision.model.ScoreDecision;
import br.com.wallet.fraud.decision.model.TextDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FraudDecisionQuestions Standard Catalog Tests")
class FraudDecisionQuestionsTest {

    @Test
    @DisplayName("should define all standard fraud questions with unique IDs and stable keys")
    void shouldDefineStandardQuestions() {
        assertThat(FraudDecisionQuestions.BEHAVIOR_ANOMALY.questionKey()).isEqualTo("BEHAVIOR_ANOMALY");
        assertThat(FraudDecisionQuestions.BEHAVIOR_ANOMALY.valueType()).isEqualTo(BooleanDecision.class);

        assertThat(FraudDecisionQuestions.SUSPECTED_MULE_RING.questionKey()).isEqualTo("SUSPECTED_MULE_RING");
        assertThat(FraudDecisionQuestions.SUSPECTED_MULE_RING.valueType()).isEqualTo(BooleanDecision.class);

        assertThat(FraudDecisionQuestions.ANOMALOUS_CASH_OUT.questionKey()).isEqualTo("ANOMALOUS_CASH_OUT");
        assertThat(FraudDecisionQuestions.ANOMALOUS_CASH_OUT.valueType()).isEqualTo(ScoreDecision.class);

        assertThat(FraudDecisionQuestions.INVESTIGATION_SUMMARY.questionKey()).isEqualTo("INVESTIGATION_SUMMARY");
        assertThat(FraudDecisionQuestions.INVESTIGATION_SUMMARY.valueType()).isEqualTo(TextDecision.class);

        List<DecisionQuestion<?>> all = FraudDecisionQuestions.allStandardQuestions();
        assertThat(all).hasSize(4);

        long uniqueIds = all.stream().map(DecisionQuestion::questionId).distinct().count();
        assertThat(uniqueIds).isEqualTo(4);

        long uniqueKeys = all.stream().map(DecisionQuestion::questionKey).distinct().count();
        assertThat(uniqueKeys).isEqualTo(4);
    }
}
