package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.infrastructure.rest.dto.DispatchEmbeddingResponse;
import br.com.wallet.infrastructure.rest.dto.ExtractEmbeddingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.UUID;

@RequestMapping("/api/v1/fraud/intelligence/embeddings")
public interface FraudEmbeddingsApi {

    @Operation(summary = "Evaluate and extract behavioral embedding vector for entity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Behavioral embeddings and archetype match evaluated")
    })
    @PostMapping("/extract/{entityId}")
    ResponseEntity<ExtractEmbeddingsResponse> extractAndEvaluate(
        @PathVariable UUID entityId
    );

    @Operation(summary = "Dispatch asynchronous embedding evaluation job")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Evaluation job enqueued"),
        @ApiResponse(responseCode = "200", description = "Active evaluation job already in progress")
    })
    @PostMapping("/dispatch/{entityId}")
    ResponseEntity<DispatchEmbeddingResponse> dispatchEvaluation(
        @PathVariable UUID entityId,
        @RequestParam(required = false) Instant asOf
    );
}
