package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.fraud.investigation.api.InvestigationService;
import br.com.wallet.fraud.investigation.api.model.FraudInvestigationDossier;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.infrastructure.rest.api.FraudInvestigationApi;
import br.com.wallet.infrastructure.rest.dto.InvestigationDossierResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
public class FraudInvestigationController implements FraudInvestigationApi {

    private final InvestigationService investigationService;

    public FraudInvestigationController(@NonNull final InvestigationService investigationService) {
        this.investigationService = Objects.requireNonNull(investigationService, "investigationService cannot be null");
    }

    @Override
    public ResponseEntity<InvestigationDossierResponse> generateDossier(
        final UUID entityId,
        final InferenceCapability capability
    ) {
        InferenceCapability cap = capability != null ? capability : InferenceCapability.BALANCED;
        FraudInvestigationDossier dossier = investigationService.generateDossier(entityId, cap);

        return ResponseEntity.ok(new InvestigationDossierResponse(
            entityId,
            dossier.classification(),
            dossier.classificationSource(),
            dossier.status(),
            dossier.allowedActions(),
            dossier.evidence(),
            dossier.narrative().orElse(null)
        ));
    }
}
