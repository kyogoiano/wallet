package br.com.wallet.decision.model;

import br.com.wallet.decision.evaluator.MismatchedDecisionTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvaluationResult Generic Type Binding & Triad 2 Tests")
class EvaluationResultTypeBindingTest {

    private final DecisionProvenance provenance = new DecisionProvenance("qwen2.5:7b", "v1.0", Instant.now(), 25L);

    @Test
    @DisplayName("POSITIVE: Should extract strongly typed outcome matching question type parameter")
    void shouldExtractStronglyTypedOutcome() {
        DecisionQuestion<BooleanDecision> boolQuestion = new DecisionQuestion<>(
            "Q-01", "BEHAVIOR_ANOMALY", "Anomaly check", BooleanDecision.class
        );
        BooleanDecision boolVal = new BooleanDecision(true, "Velocity anomaly detected");
        DecisionOutcome<BooleanDecision> boolOutcome = new DecisionAnswer<>(
            boolVal, Confidence.HIGH, List.of(), provenance
        );

        EvaluatedQuestion<BooleanDecision> evaluated = new EvaluatedQuestion<>(boolQuestion, boolOutcome);
        EvaluationResult result = new EvaluationResult("EVAL-1", "USER-100", List.of(evaluated), Instant.now());

        DecisionOutcome<BooleanDecision> extractedOutcome = result.outcomeFor(boolQuestion);
        assertThat(extractedOutcome).isInstanceOf(DecisionAnswer.class);

        DecisionAnswer<BooleanDecision> answer = (DecisionAnswer<BooleanDecision>) extractedOutcome;
        assertThat(answer.value().value()).isTrue();
        assertThat(answer.value().rationale()).isEqualTo("Velocity anomaly detected");
    }

    @Test
    @DisplayName("NEGATIVE: Passing mismatched runtime type witness throws MismatchedDecisionTypeException")
    void shouldThrowWhenRuntimeTypeWitnessMismatches() {
        DecisionQuestion<BooleanDecision> originalQuestion = new DecisionQuestion<>(
            "Q-01", "BEHAVIOR_ANOMALY", "Anomaly check", BooleanDecision.class
        );
        BooleanDecision boolVal = new BooleanDecision(true, "Anomaly");
        DecisionOutcome<BooleanDecision> outcome = new DecisionAnswer<>(
            boolVal, Confidence.HIGH, List.of(), provenance
        );
        EvaluatedQuestion<BooleanDecision> evaluated = new EvaluatedQuestion<>(originalQuestion, outcome);
        EvaluationResult result = new EvaluationResult("EVAL-1", "USER-100", List.of(evaluated), Instant.now());

        // Construct question with identical ID but conflicting type witness (ScoreDecision instead of BooleanDecision)
        DecisionQuestion<ScoreDecision> spoofedQuestion = new DecisionQuestion<>(
            "Q-01", "BEHAVIOR_ANOMALY", "Anomaly check", ScoreDecision.class
        );

        assertThatThrownBy(() -> result.outcomeFor(spoofedQuestion))
            .isInstanceOf(MismatchedDecisionTypeException.class)
            .hasMessageContaining("Expected")
            .hasMessageContaining("ScoreDecision");
    }

    @Test
    @DisplayName("BOUNDARY: Requesting unasked question throws NoSuchElementException")
    void shouldThrowWhenQuestionNotFound() {
        DecisionQuestion<BooleanDecision> q1 = new DecisionQuestion<>(
            "Q-01", "ANOMALY", "Desc", BooleanDecision.class
        );
        DecisionQuestion<BooleanDecision> q2 = new DecisionQuestion<>(
            "Q-02", "MULE_RING", "Desc", BooleanDecision.class
        );

        EvaluatedQuestion<BooleanDecision> evaluated = new EvaluatedQuestion<>(
            q1, new DecisionUnavailable<>(UnavailableReason.TIMEOUT, "Timeout", provenance)
        );
        EvaluationResult result = new EvaluationResult("EVAL-1", "USER-100", List.of(evaluated), Instant.now());

        assertThatThrownBy(() -> result.outcomeFor(q2))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Question not found: Q-02");
    }
}
