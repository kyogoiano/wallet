package br.com.wallet.decision.composition;

import br.com.wallet.decision.model.BooleanDecision;
import br.com.wallet.decision.model.DecisionAnswer;
import br.com.wallet.decision.model.DecisionOutcome;
import br.com.wallet.decision.model.DecisionQuestion;
import br.com.wallet.decision.model.DecisionUnavailable;
import br.com.wallet.decision.model.EvaluatedQuestion;
import br.com.wallet.decision.model.EvaluationResult;
import br.com.wallet.decision.model.ScoreDecision;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Domain aggregator composing individual typed decision outcomes into actionable compound risk assessments.
 *
 * <p><b>ANTI-COERCION GATE (I-TYPED-006):</b>
 * Unavailable decisions MUST NOT be coerced into {@code false}, {@code 0.00}, or synthetic default scores.
 */
@Component
public class DecisionComposer {

    private static final BigDecimal HIGH_SCORE_THRESHOLD = new BigDecimal("0.70");

    /**
     * Composes evaluation results into a CompoundRiskAssessment according to the given policy.
     *
     * @param result the batch evaluation result
     * @param policy aggregation and failure handling policy
     * @param mandatoryQuestions set of questions strictly required for completion
     * @return compound risk assessment
     */
    public CompoundRiskAssessment compose(
        final EvaluationResult result,
        final CompositionPolicy policy,
        final Set<DecisionQuestion<?>> mandatoryQuestions
    ) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        final Set<DecisionQuestion<?>> mandatory = mandatoryQuestions != null ? mandatoryQuestions : Set.of();

        List<DecisionUnavailable<?>> unavailables = new ArrayList<>();
        Set<String> triggeredSignals = new HashSet<>();

        // Check each evaluated question
        for (final EvaluatedQuestion<?> eq : result.questions()) {
            DecisionOutcome<?> outcome = eq.outcome();
            if (outcome instanceof DecisionUnavailable<?> unavailable) {
                unavailables.add(unavailable);
                if (mandatory.contains(eq.question())) {
                    if (policy == CompositionPolicy.FAIL_CLOSED || policy == CompositionPolicy.REQUIRE_MANDATORY_QUESTIONS) {
                        return new CompoundRiskAssessment(
                            AssessmentStatus.INCONCLUSIVE,
                            Set.of(),
                            List.of(unavailable),
                            "Mandatory question unavailable: " + eq.question().questionId() + " (" + unavailable.diagnosticMessage() + ")"
                        );
                    }
                }
            } else if (outcome instanceof DecisionAnswer<?> answer) {
                // Pattern match on verified answers without coercion
                if (answer.value() instanceof BooleanDecision boolVal) {
                    if (boolVal.value()) {
                        triggeredSignals.add(eq.question().questionKey());
                    }
                } else if (answer.value() instanceof ScoreDecision scoreVal) {
                    if (scoreVal.score().compareTo(HIGH_SCORE_THRESHOLD) >= 0) {
                        if ("ANOMALOUS_CASH_OUT".equals(eq.question().questionKey())) {
                            triggeredSignals.add("HIGH_CASHOUT_RISK");
                        } else {
                            triggeredSignals.add(eq.question().questionKey());
                        }
                    }
                }
            }
        }

        if (!unavailables.isEmpty()) {
            if (policy == CompositionPolicy.FAIL_CLOSED) {
                return new CompoundRiskAssessment(
                    AssessmentStatus.INCONCLUSIVE,
                    Set.of(),
                    unavailables,
                    "Evaluations contained " + unavailables.size() + " unavailable outcomes under FAIL_CLOSED policy"
                );
            }
            if (policy == CompositionPolicy.DEGRADE_TO_UNVERIFIED) {
                return new CompoundRiskAssessment(
                    AssessmentStatus.UNVERIFIED_PARTIAL,
                    triggeredSignals,
                    unavailables,
                    "Contains unverified or unavailable evaluations: " + unavailables.size()
                );
            }
        }

        return new CompoundRiskAssessment(
            AssessmentStatus.VERIFIED,
            triggeredSignals,
            unavailables,
            "Deterministic semantic composition complete"
        );
    }
}
