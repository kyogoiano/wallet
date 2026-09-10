package br.com.wallet.edge.internal.ingress;

import br.com.wallet.edge.api.EdgeCommandIngress;
import br.com.wallet.edge.api.EdgeCommandResult;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Non-blocking Edge Gateway REST Controller (PLAN-000.9 Section 1).
 * Accepts financial commands, delegates to CommandAcceptanceService, and returns 202 ACCEPTED
 * with Location header pointing to real-time status stream.
 */
@RestController
@RequestMapping("/operations")
public class EdgeOperationsController {

    private final EdgeCommandIngress ingress;

    public EdgeOperationsController(EdgeCommandIngress ingress) {
        this.ingress = ingress;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<ResponseEntity<?>> acceptTransfer(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @RequestBody String requestJson,
            HttpServletRequest request
    ) {
        return handleCommand(CommandType.TRANSFER, idempotencyKey, requestJson, request);
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<ResponseEntity<?>> acceptDeposit(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @RequestBody String requestJson,
            HttpServletRequest request
    ) {
        return handleCommand(CommandType.DEPOSIT, idempotencyKey, requestJson, request);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<ResponseEntity<?>> acceptWithdrawal(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @RequestBody String requestJson,
            HttpServletRequest request
    ) {
        return handleCommand(CommandType.WITHDRAW, idempotencyKey, requestJson, request);
    }

    private CompletableFuture<ResponseEntity<?>> handleCommand(
            CommandType type,
            UUID idempotencyKey,
            String requestJson,
            HttpServletRequest request
    ) {
        UUID opId = idempotencyKey != null ? idempotencyKey : UUID.randomUUID();
        String clientIp = request != null ? request.getRemoteAddr() : "127.0.0.1";

        CommandEnvelope envelope = CommandEnvelope.create(opId, type, requestJson, clientIp);

        return ingress.acceptCommand(envelope).thenApply(result -> mapResultToResponse(result, opId));
    }

    private ResponseEntity<?> mapResultToResponse(EdgeCommandResult result, UUID opId) {
        return switch (result) {
            case EdgeCommandResult.Accepted accepted -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .location(URI.create(accepted.location()))
                    .header("X-Edge-Spooled", String.valueOf(accepted.spooledDegraded()))
                    .body(new OperationStatusResponse(
                            accepted.operationId(),
                            OperationStatusResponse.STATUS_PROCESSING,
                            Instant.now(),
                            "Command accepted for execution"
                    ));

            case EdgeCommandResult.RateLimited rateLimited -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimited.retryAfterSeconds()))
                    .body(new ErrorResponse("RATE_LIMITED", rateLimited.reason()));

            case EdgeCommandResult.BulkheadFull bulkheadFull -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, "1")
                    .body(new ErrorResponse("BULKHEAD_FULL", "Edge ingress concurrency saturated (" +
                            bulkheadFull.currentInflight() + "/" + bulkheadFull.maxInflight() + ")"));

            case EdgeCommandResult.ContentTooLarge contentTooLarge -> ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                    .body(new ErrorResponse("CONTENT_TOO_LARGE", "Command payload size " +
                            contentTooLarge.payloadBytes() + " bytes exceeds limit of " + contentTooLarge.maxBytes() + " bytes"));

            case EdgeCommandResult.Saturated saturated -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(saturated.retryAfterSeconds()))
                    .body(new ErrorResponse("SERVICE_SATURATED", saturated.reason()));

            case EdgeCommandResult.Conflict conflict -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("OPERATION_CONFLICT", conflict.reason()));

            case EdgeCommandResult.Rejected rejected -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("COMMAND_REJECTED", rejected.reason()));
        };
    }

    public record ErrorResponse(String error, String message) {}
}
