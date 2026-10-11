package br.com.wallet.decision.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DecisionQuestion Generic Contract Tests")
class DecisionQuestionTest {

    @Test
    @DisplayName("should construct typed question with runtime type witness")
    void shouldConstructTypedQuestion() {
        DecisionQuestion<BooleanDecision> question = new DecisionQuestion<>(
            "Q-001",
            "BEHAVIOR_ANOMALY",
            "Is transaction volume anomalous compared to history?",
            BooleanDecision.class
        );

        assertThat(question.questionId()).isEqualTo("Q-001");
        assertThat(question.questionKey()).isEqualTo("BEHAVIOR_ANOMALY");
        assertThat(question.description()).isEqualTo("Is transaction volume anomalous compared to history?");
        assertThat(question.valueType()).isEqualTo(BooleanDecision.class);
    }

    @Test
    @DisplayName("should reject null fields in question definition")
    void shouldRejectNulls() {
        assertThatThrownBy(() -> new DecisionQuestion<>(null, "KEY", "Desc", BooleanDecision.class))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("questionId must not be null");

        assertThatThrownBy(() -> new DecisionQuestion<>("ID", null, "Desc", BooleanDecision.class))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("questionKey must not be null");

        assertThatThrownBy(() -> new DecisionQuestion<>("ID", "KEY", null, BooleanDecision.class))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("description must not be null");

        assertThatThrownBy(() -> new DecisionQuestion<>("ID", "KEY", "Desc", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("valueType must not be null");
    }
}
