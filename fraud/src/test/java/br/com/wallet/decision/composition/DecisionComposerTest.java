package br.com.wallet.decision.composition;

import br.com.wallet.decision.catalog.FraudDecisionQuestions;
import br.com.wallet.decision.model.BooleanDecision;
import br.com.wallet.decision.model.Confidence;
import br.com.wallet.decision.model.DecisionAnswer;
import br.com.wallet.decision.model.DecisionOutcome;
import br.com.wallet.decision.model.DecisionProvenance;
import br.com.wallet.decision.model.DecisionUnavailable;
import br.com.wallet.decision.model.EvaluatedQuestion;
import br.com.wallet.decision.model.EvaluationResult;
import br.com.wallet.decision.model.ScoreDecision;
import br.com.wallet.decision.model.UnavailableReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DecisionComposer Anti-Coercion & Triad 4 Tests (I-TYPED-006)")
class DecisionComposerTest {

    private final DecisionProvenance prov = new DecisionProvenance("test-slm", "v1", Instant.now(), 10L);
    private final DecisionComposer composer = new DecisionComposer();

    @Test
    @DisplayName("POSITIVE: All questions answered -> computes deterministic CompoundRiskAssessment")
    void shouldComposeWhenAllAnswersPresent() {
        DecisionOutcome<BooleanDecision> anomalyOutcome = new DecisionAnswer<>(
            new BooleanDecision(true, "Surge detected"), Confidence.HIGH, List.of(), prov
        );
        DecisionOutcome<BooleanDecision> muleOutcome = new DecisionAnswer<>(
            new BooleanDecision(false, "No mule topology"), Confidence.HIGH, List.of(), prov
        );
        DecisionOutcome<ScoreDecision> cashOutOutcome = new DecisionAnswer<>(
            new ScoreDecision(new BigDecimal("0.80"), "Rapid drain"), Confidence.HIGH, List.of(), prov
        );

        EvaluationResult result = new EvaluationResult(
            "EVAL-100",
            "USER-1",
            List.of(
                new EvaluatedQuestion<>(FraudDecisionQuestions.BEHAVIOR_ANOMALY, anomalyOutcome),
                new EvaluatedQuestion<>(FraudDecisionQuestions.SUSPECTED_MULE_RING, muleOutcome),
                new EvaluatedQuestion<>(FraudDecisionQuestions.ANOMALOUS_CASH_OUT, cashOutOutcome)
            ),
            Instant.now()
        );

        CompoundRiskAssessment assessment = composer.compose(
            result,
            CompositionPolicy.FAIL_CLOSED,
            Set.of(FraudDecisionQuestions.BEHAVIOR_ANOMALY, FraudDecisionQuestions.SUSPECTED_MULE_RING)
        );

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.VERIFIED);
        assertThat(assessment.triggeredSignals()).containsExactlyInAnyOrder("BEHAVIOR_ANOMALY", "HIGH_CASHOUT_RISK");
        assertThat(assessment.unavailables()).isEmpty();
    }

    @Test
    @DisplayName("NEGATIVE / BOUNDARY: Mandatory question unavailable under FAIL_CLOSED returns INCONCLUSIVE")
    void shouldRefuseCompositionWhenMandatoryQuestionUnavailable() {
        DecisionOutcome<BooleanDecision> anomalyOutcome = new DecisionUnavailable<>(
            UnavailableReason.TIMEOUT, "Evaluator timed out", prov
        );
        DecisionOutcome<BooleanDecision> muleOutcome = new DecisionAnswer<>(
            new BooleanDecision(false, "Clean"), Confidence.HIGH, List.of(), prov
        );

        EvaluationResult result = new EvaluationResult(
            "EVAL-101",
            "USER-2",
            List.of(
                new EvaluatedQuestion<>(FraudDecisionQuestions.BEHAVIOR_ANOMALY, anomalyOutcome),
                new EvaluatedQuestion<>(FraudDecisionQuestions.SUSPECTED_MULE_RING, muleOutcome)
            ),
            Instant.now()
        );

        CompoundRiskAssessment assessment = composer.compose(
            result,
            CompositionPolicy.FAIL_CLOSED,
            Set.of(FraudDecisionQuestions.BEHAVIOR_ANOMALY)
        );

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INCONCLUSIVE);
        assertThat(assessment.unavailables()).hasSize(1);
        assertThat(assessment.unavailables().getFirst().reason()).isEqualTo(UnavailableReason.TIMEOUT);
        assertThat(assessment.explanation()).contains("Mandatory question unavailable: Q-FRAUD-001");
    }

    @Test
    @DisplayName("ANTI-COERCION GATE: Unavailable question MUST NOT be coerced to false or 0.00 (I-TYPED-006)")
    void unavailableMustNotBeCoercedToFalseOrZero() {
        DecisionOutcome<BooleanDecision> anomalyUnavailable = new DecisionUnavailable<>(
            UnavailableReason.INSUFFICIENT_EVIDENCE, "No history", prov
        );

        EvaluationResult result = new EvaluationResult(
            "EVAL-102",
            "USER-3",
            List.of(new EvaluatedQuestion<>(FraudDecisionQuestions.BEHAVIOR_ANOMALY, anomalyUnavailable)),
            Instant.now()
        );

        CompoundRiskAssessment assessment = composer.compose(
            result,
            CompositionPolicy.DEGRADE_TO_UNVERIFIED,
            Set.of()
        );

        // Under DEGRADE_TO_UNVERIFIED, the status is UNVERIFIED_PARTIAL, not VERIFIED clean/false!
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.UNVERIFIED_PARTIAL);
        assertThat(assessment.unavailables()).hasSize(1);
        // It must NOT record that BEHAVIOR_ANOMALY is false/negative!
        assertThat(assessment.triggeredSignals()).doesNotContain("BEHAVIOR_ANOMALY");
        assertThat(assessment.explanation()).contains("Contains unverified or unavailable evaluations: 1");
    }
}
