package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.fraud.fusion.api.model.AnalystReviewRequest;
import br.com.wallet.fraud.fusion.api.model.AnalystReviewResponse;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.infrastructure.rest.dto.DispatchFusionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RequestMapping("/api/v1/fraud/intelligence/fusion")
public interface FraudSignalFusionApi {

    @Operation(summary = "Dispatch asynchronous risk fusion evaluation job")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Evaluation job enqueued or coalesced")
    })
    @PostMapping(value = "/dispatch/{entityId}", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    ResponseEntity<DispatchFusionResponse> dispatchEvaluation(
        @PathVariable UUID entityId,
        @RequestParam(required = false) Instant asOf,
        @RequestParam(defaultValue = "v1") String modelVersion,
        @RequestBody(required = false) Map<String, Object> payload
    );

    @Operation(summary = "Synchronously evaluate multi-signal risk fusion for entity (Investigation / Debug / Audit)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Risk fusion evaluation result")
    })
    @PostMapping("/evaluate/{entityId}")
    ResponseEntity<RiskFusionResult> evaluateEntity(
        @PathVariable UUID entityId
    );

    @Operation(summary = "Submit human-in-the-loop compliance analyst review verdict for checkpoint")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Review successfully submitted and checkpoint updated"),
        @ApiResponse(responseCode = "404", description = "Checkpoint not found")
    })
    @PostMapping(value = "/reviews/{checkpointId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AnalystReviewResponse> submitReview(
        @PathVariable UUID checkpointId,
        @RequestBody AnalystReviewRequest request
    );
}
