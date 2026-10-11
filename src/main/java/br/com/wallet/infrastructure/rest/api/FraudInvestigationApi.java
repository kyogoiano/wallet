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

import br.com.wallet.fraud.investigation.api.model.TypedEvaluationResponse;

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

    @Operation(summary = "Evaluate typed AI fraud decisions for entity (REQ-TYPED-018)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Typed decision evaluation generated")
    })
    @org.springframework.web.bind.annotation.PostMapping(value = {"/{entityId}/evaluate", "/evaluate/{entityId}"})
    ResponseEntity<TypedEvaluationResponse> evaluateDecisions(
        @PathVariable UUID entityId
    );

    @Operation(summary = "Get latest typed AI fraud decisions for entity (REQ-TYPED-018)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Typed decision evaluation retrieved")
    })
    @GetMapping(value = {"/{entityId}/decisions", "/decisions/{entityId}"})
    ResponseEntity<TypedEvaluationResponse> getDecisions(
        @PathVariable UUID entityId
    );
}
