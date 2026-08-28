package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.dlq.api.dto.DiscardDlqCommand;
import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.ReplayExhaustedResult;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "DLQ & Operational Recovery API", description = "Endpoints for inspecting, manually replaying, and discarding Dead Letter Queue operations")
public interface DlqApi {

    @Operation(summary = "Query DLQ operations with optional status and failure filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of DLQ operations returned")
    })
    ResponseEntity<List<DlqOperationResponse>> listOperations(
            DlqStatus status,
            DlqFailureType failureType,
            String eventType,
            UUID operationId,
            Integer limit,
            Integer offset
    );

    @Operation(summary = "Get a specific DLQ operation by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operation found"),
            @ApiResponse(responseCode = "404", description = "Operation not found")
    })
    ResponseEntity<DlqOperationResponse> getOperation(UUID id);

    @Operation(summary = "Manually replay a DLQ operation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operation replayed successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot replay already completed operation"),
            @ApiResponse(responseCode = "404", description = "Operation not found")
    })
    ResponseEntity<DlqOperationResponse> replayOperation(UUID id);

    @Operation(summary = "Manually discard a DLQ operation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operation discarded"),
            @ApiResponse(responseCode = "400", description = "Cannot discard completed operation"),
            @ApiResponse(responseCode = "404", description = "Operation not found")
    })
    ResponseEntity<DlqOperationResponse> discardOperation(UUID id, @Valid DiscardDlqCommand command);

    @Operation(summary = "Batch replay all currently exhausted operations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch replay triggered")
    })
    ResponseEntity<ReplayExhaustedResult> replayAllExhausted();
}
