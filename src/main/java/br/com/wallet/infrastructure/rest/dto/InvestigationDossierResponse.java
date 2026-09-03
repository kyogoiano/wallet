package br.com.wallet.infrastructure.rest.dto;

import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationGenerationStatus;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.api.model.RiskClassificationSource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record InvestigationDossierResponse(
    @NonNull UUID entityId,
    @NonNull RiskClassification classification,
    @NonNull RiskClassificationSource classificationSource,
    @NonNull InvestigationGenerationStatus status,
    @NonNull List<RecommendedAction> allowedActions,
    @NonNull InvestigationEvidence evidence,
    @Nullable InvestigationNarrative narrative
) {}
