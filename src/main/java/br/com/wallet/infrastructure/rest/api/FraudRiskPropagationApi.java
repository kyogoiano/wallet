package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.infrastructure.rest.dto.DispatchPropagationResponse;
import br.com.wallet.infrastructure.rest.dto.FraudRiskPropagationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.UUID;

@RequestMapping("/api/v1/fraud/intelligence/propagation")
public interface FraudRiskPropagationApi {

    @Operation(summary = "Dispatch asynchronous risk propagation evaluation job")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Evaluation job enqueued"),
        @ApiResponse(responseCode = "200", description = "Active evaluation job already in progress")
    })
    @PostMapping("/dispatch/{entityId}")
    ResponseEntity<DispatchPropagationResponse> dispatchEvaluation(
        @PathVariable UUID entityId,
        @RequestParam(required = false) Instant asOf,
        @RequestParam(defaultValue = "v1") String modelVersion
    );

    @Operation(summary = "Synchronously evaluate risk propagation for entity (Investigation / Debug)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Propagation evaluation result")
    })
    @GetMapping("/evaluate/{entityId}")
    ResponseEntity<FraudRiskPropagationResponse> evaluateEntity(
        @PathVariable UUID entityId,
        @RequestParam(required = false) Instant asOf
    );
}
