package br.com.wallet.fraud.investigation.api.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete investigation dossier combining deterministic evidence, deterministic classification
 * and allowed actions, optional grounded narrative, and generation status.
 */
public record FraudInvestigationDossier(
    @NonNull InvestigationEvidence evidence,
    Optional<InvestigationNarrative> narrative,
    @NonNull RiskClassification classification,
    @NonNull RiskClassificationSource classificationSource,
    @NonNull List<RecommendedAction> allowedActions,
    @NonNull InvestigationGenerationStatus status
) {
    public FraudInvestigationDossier {
        Objects.requireNonNull(evidence, "evidence cannot be null");
        Objects.requireNonNull(narrative, "narrative cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");
        Objects.requireNonNull(classificationSource, "classificationSource cannot be null");
        Objects.requireNonNull(allowedActions, "allowedActions cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        allowedActions = List.copyOf(allowedActions);
    }
}
