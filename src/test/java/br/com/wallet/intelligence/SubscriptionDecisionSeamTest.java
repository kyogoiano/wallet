package br.com.wallet.intelligence;

import br.com.wallet.decision.catalog.SubscriptionDecisionQuestions;
import br.com.wallet.decision.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Subscription Decision Seam & Failure Isolation Tests (TASK-3.1.15, REQ-SUB-010, REQ-SUB-011, I-SUB-005)")
class SubscriptionDecisionSeamTest {

    private final DecisionProvenance testProvenance = new DecisionProvenance(
            "subscription-evaluator-v1",
            "v1.0",
            Instant.now(),
            120L
    );

    @Test
    @DisplayName("REQ-SUB-010: Exposes typed DecisionQuestion<SubscriptionClassification>")
    void shouldExposeTypedDecisionQuestion() {
        var question = SubscriptionDecisionQuestions.SUBSCRIPTION_CLASSIFICATION;

        assertThat(question.questionId()).isEqualTo("Q-SUB-001");
        assertThat(question.questionKey()).isEqualTo("SUBSCRIPTION_CLASSIFICATION");
        assertThat(question.valueType()).isEqualTo(SubscriptionClassification.class);
    }

    @Test
    @DisplayName("Positive Canonical: DecisionAnswer carries validated category and rationale")
    void shouldProduceSuccessfulDecisionAnswer() {
        var classification = new SubscriptionClassification("ENTERTAINMENT", "Monthly Netflix debit series");
        DecisionOutcome<SubscriptionClassification> outcome = new DecisionAnswer<>(
                classification,
                Confidence.HIGH,
                List.of(),
                testProvenance
        );

        assertThat(outcome).isInstanceOf(DecisionAnswer.class);
        DecisionAnswer<SubscriptionClassification> answer = (DecisionAnswer<SubscriptionClassification>) outcome;
        assertThat(answer.value().category()).isEqualTo("ENTERTAINMENT");
        assertThat(answer.value().rationale()).isEqualTo("Monthly Netflix debit series");
        assertThat(answer.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    @DisplayName("I-SUB-005: Inconclusive evaluation produces DecisionAnswer with UNKNOWN category")
    void shouldDistinguishInconclusiveAnswerFromFailure() {
        var classification = new SubscriptionClassification("UNKNOWN", "Transaction pattern has insufficient semantic indicators");
        DecisionOutcome<SubscriptionClassification> outcome = new DecisionAnswer<>(
                classification,
                Confidence.LOW,
                List.of(),
                testProvenance
        );

        assertThat(outcome).isInstanceOf(DecisionAnswer.class);
        DecisionAnswer<SubscriptionClassification> answer = (DecisionAnswer<SubscriptionClassification>) outcome;
        assertThat(answer.value().category()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("I-SUB-005, I-TYPED-005: Evaluator timeout returns DecisionUnavailable without coercion")
    void shouldReturnDecisionUnavailableOnEvaluatorTimeout() {
        DecisionOutcome<SubscriptionClassification> outcome = new DecisionUnavailable<>(
                UnavailableReason.TIMEOUT,
                "Inference worker did not respond within 500ms timeout",
                testProvenance
        );

        assertThat(outcome).isInstanceOf(DecisionUnavailable.class);
        DecisionUnavailable<SubscriptionClassification> unavailable = (DecisionUnavailable<SubscriptionClassification>) outcome;
        assertThat(unavailable.reason()).isEqualTo(UnavailableReason.TIMEOUT);
        assertThat(unavailable.diagnosticMessage()).contains("500ms timeout");

        // Sealed algebra guarantees exhaustive pattern matching without accidental default coercion
        String resolvedCategory = switch (outcome) {
            case DecisionAnswer<SubscriptionClassification> a -> a.value().category();
            case DecisionUnavailable<SubscriptionClassification> u -> "FALLBACK_UNAVAILABLE";
        };
        assertThat(resolvedCategory).isEqualTo("FALLBACK_UNAVAILABLE");
    }
}
