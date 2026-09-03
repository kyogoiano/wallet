package br.com.wallet.fraud.investigation.api;

import br.com.wallet.fraud.investigation.api.model.FraudInvestigationDossier;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Public API for generating explainable, evidence-grounded fraud investigation dossiers.
 */
public interface InvestigationService {

    @NonNull
    FraudInvestigationDossier generateDossier(@NonNull UUID entityId);

    @NonNull
    FraudInvestigationDossier generateDossier(@NonNull UUID entityId, @NonNull InferenceCapability capability);
}
