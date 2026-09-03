package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.infrastructure.rest.dto.InvestigationDossierResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@RequestMapping("/api/v1/fraud/intelligence/investigation")
public interface FraudInvestigationApi {

    @Operation(summary = "Generate explainable investigation dossier for entity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Investigation dossier generated")
    })
    @GetMapping("/dossier/{entityId}")
    ResponseEntity<InvestigationDossierResponse> generateDossier(
        @PathVariable UUID entityId,
        @RequestParam(defaultValue = "BALANCED") InferenceCapability capability
    );
}
