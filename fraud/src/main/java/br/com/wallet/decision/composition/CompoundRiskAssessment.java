package br.com.wallet.decision.composition;

import br.com.wallet.decision.model.DecisionUnavailable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Result of composing multiple typed semantic question outcomes into a domain risk assessment.
 *
 * @param status verification status of the assessment
 * @param triggeredSignals set of verified risk signals identified
 * @param unavailables list of unavailable outcomes encountered
 * @param explanation human-readable summary of the assessment composition
 */
public record CompoundRiskAssessment(
    AssessmentStatus status,
    Set<String> triggeredSignals,
    List<DecisionUnavailable<?>> unavailables,
    String explanation
) {

    public CompoundRiskAssessment {
        Objects.requireNonNull(status, "status must not be null");
        triggeredSignals = Set.copyOf(triggeredSignals != null ? triggeredSignals : Set.of());
        unavailables = List.copyOf(unavailables != null ? unavailables : List.of());
        Objects.requireNonNull(explanation, "explanation must not be null");
    }
}
