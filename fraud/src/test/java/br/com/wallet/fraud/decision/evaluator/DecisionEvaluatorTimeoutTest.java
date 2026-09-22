package br.com.wallet.fraud.decision.evaluator;

import br.com.wallet.fraud.decision.catalog.FraudDecisionQuestions;
import br.com.wallet.fraud.decision.model.Confidence;
import br.com.wallet.fraud.decision.model.DecisionAnswer;
import br.com.wallet.fraud.decision.model.DecisionOutcome;
import br.com.wallet.fraud.decision.model.DecisionUnavailable;
import br.com.wallet.fraud.decision.model.EvaluationResult;
import br.com.wallet.fraud.decision.model.ScoreDecision;
import br.com.wallet.fraud.decision.model.UnavailableReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DecisionEvaluator Timeout Containment & Triad 3 Tests (I-TYPED-005)")
class DecisionEvaluatorTimeoutTest {

    @Test
    @DisplayName("POSITIVE: Completing within timeout returns DecisionAnswer with calibrated score")
    void shouldReturnDecisionAnswerWhenFast() {
        OllamaDecisionEvaluator evaluator = new OllamaDecisionEvaluator(
            (subjectId, questions, evidence) -> CompletableFuture.completedFuture(
                Map.of("Q-FRAUD-003", new ScoreDecision(new BigDecimal("0.75"), "Elevated cash-out velocity"))
            ),
            Duration.ofMillis(500)
        );

        EvaluationResult result = evaluator.evaluate(
            "USER-1",
            List.of(FraudDecisionQuestions.ANOMALOUS_CASH_OUT),
            Map.of()
        ).join();

        DecisionOutcome<ScoreDecision> outcome = result.outcomeFor(FraudDecisionQuestions.ANOMALOUS_CASH_OUT);
        assertThat(outcome).isInstanceOf(DecisionAnswer.class);

        DecisionAnswer<ScoreDecision> answer = (DecisionAnswer<ScoreDecision>) outcome;
        assertThat(answer.value().score()).isEqualByComparingTo("0.75");
        assertThat(answer.confidence()).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    @DisplayName("BOUNDARY / TRIAD 3: Simulating evaluator timeout returns DecisionUnavailable without synthetic score or confidence")
    void shouldReturnDecisionUnavailableOnTimeoutWithoutScoreOrConfidence() {
        // Evaluator that never completes or takes longer than timeout
        OllamaDecisionEvaluator evaluator = new OllamaDecisionEvaluator(
            (subjectId, questions, evidence) -> CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Map.of("Q-FRAUD-003", new ScoreDecision(new BigDecimal("0.99"), "Should never be seen"));
            }),
            Duration.ofMillis(50) // Tight 50ms timeout
        );

        EvaluationResult result = evaluator.evaluate(
            "USER-2",
            List.of(FraudDecisionQuestions.ANOMALOUS_CASH_OUT),
            Map.of()
        ).join();

        DecisionOutcome<ScoreDecision> outcome = result.outcomeFor(FraudDecisionQuestions.ANOMALOUS_CASH_OUT);
        assertThat(outcome).isInstanceOf(DecisionUnavailable.class);

        DecisionUnavailable<ScoreDecision> unavailable = (DecisionUnavailable<ScoreDecision>) outcome;
        assertThat(unavailable.reason()).isEqualTo(UnavailableReason.TIMEOUT);
        assertThat(unavailable.diagnosticMessage()).contains("timeout");

        // Verify structural absence of score and confidence methods on DecisionUnavailable
        List<String> methodNames = Arrays.stream(unavailable.getClass().getMethods())
            .map(Method::getName)
            .toList();
        assertThat(methodNames).doesNotContain("score", "confidence", "value");
    }
}
