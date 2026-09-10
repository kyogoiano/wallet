package br.com.wallet.edge.internal.command;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.EdgeCommandIngress;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.api.EdgeCommandResult;
import br.com.wallet.edge.internal.idempotency.EdgeIdempotencyGate;
import br.com.wallet.edge.internal.ingress.EdgeRequestValidator;
import br.com.wallet.edge.internal.journal.spi.DurableSpilloverJournal;
import br.com.wallet.edge.internal.resilience.BrokerCircuitBreaker;
import br.com.wallet.edge.internal.resilience.IngressBulkhead;
import br.com.wallet.edge.internal.resilience.PerimeterRateLimiter;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Core Command Acceptance Service (TASK-3.2, REQ-EDG-001, REQ-EDG-002, I-EDGE-001).
 * Decouples caller threads from ledger transactions.
 * Orchestrates:
 * 0. Deterministic idempotency & payload conflict gate (TASK-3.7, TASK-3.8, I-IDEMPOTENCY-001)
 * 1. 64KB envelope limit (REQ-EDG-015)
 * 2. Lock-free perimeter rate limiting (REQ-EDG-004)
 * 3. Bounded bulkhead concurrency (REQ-EDG-005, I-EDGE-002)
 * 4. Primary NATS JetStream publisher with circuit breaker (REQ-EDG-006)
 * 5. Degraded spillover to preallocated SegmentedFileJournal with group-commit fsync (I-EDGE-001)
 * 6. Saturated shedding with HTTP 503 Retry-After: 5 (REQ-EDG-003, I-EDGE-005)
 */
public class CommandAcceptanceService implements EdgeCommandIngress {

    private final EdgeCommandPublisher publisher;
    private final DurableSpilloverJournal journal;
    private final PerimeterRateLimiter rateLimiter;
    private final IngressBulkhead bulkhead;
    private final BrokerCircuitBreaker circuitBreaker;
    private final EdgeRequestValidator validator;
    private final EdgeIdempotencyGate idempotencyGate;

    public CommandAcceptanceService(
            EdgeCommandPublisher publisher,
            DurableSpilloverJournal journal,
            PerimeterRateLimiter rateLimiter,
            IngressBulkhead bulkhead,
            BrokerCircuitBreaker circuitBreaker,
            EdgeRequestValidator validator
    ) {
        this(publisher, journal, rateLimiter, bulkhead, circuitBreaker, validator, new EdgeIdempotencyGate());
    }

    public CommandAcceptanceService(
            EdgeCommandPublisher publisher,
            DurableSpilloverJournal journal,
            PerimeterRateLimiter rateLimiter,
            IngressBulkhead bulkhead,
            BrokerCircuitBreaker circuitBreaker,
            EdgeRequestValidator validator,
            EdgeIdempotencyGate idempotencyGate
    ) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.journal = Objects.requireNonNull(journal, "journal must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead must not be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.idempotencyGate = Objects.requireNonNull(idempotencyGate, "idempotencyGate must not be null");
    }

    @Override
    public CompletableFuture<EdgeCommandResult> acceptCommand(CommandEnvelope command) {
        // 0. Idempotency Conflict Gate (TASK-3.7, TASK-3.8, I-IDEMPOTENCY-001)
        EdgeIdempotencyGate.ValidationResult idempResult = idempotencyGate.validateAndRecord(command);
        if (idempResult == EdgeIdempotencyGate.ValidationResult.CONFLICT) {
            return CompletableFuture.completedFuture(
                    new EdgeCommandResult.Conflict("OperationId " + command.operationId() + " reused with conflicting payload")
            );
        }
        if (idempResult == EdgeIdempotencyGate.ValidationResult.IDEMPOTENT_REPLAY) {
            return CompletableFuture.completedFuture(
                    new EdgeCommandResult.Accepted(command.operationId(), "/operations/" + command.operationId(), false)
            );
        }

        // 1. Envelope Size Gate (REQ-EDG-015)
        if (!validator.isPayloadValid(command.payloadJson())) {
            int actualBytes = validator.getPayloadByteCount(command.payloadJson());
            return CompletableFuture.completedFuture(
                    new EdgeCommandResult.ContentTooLarge(actualBytes, validator.maxPayloadBytes())
            );
        }

        // 2. Perimeter Rate Limiting (REQ-EDG-004)
        if (!rateLimiter.tryAcquire(command.clientIp())) {
            return CompletableFuture.completedFuture(
                    new EdgeCommandResult.RateLimited("Perimeter rate limit exceeded", rateLimiter.getRetryAfterSeconds())
            );
        }

        // 3. Bounded Bulkhead Gate (REQ-EDG-005, I-EDGE-002)
        if (!bulkhead.tryAcquire()) {
            return CompletableFuture.completedFuture(
                    new EdgeCommandResult.BulkheadFull(bulkhead.getCurrentInflight(), bulkhead.getMaxInflight())
            );
        }

        CompletableFuture<EdgeCommandResult> resultFuture;

        // 4. Primary Broker vs Spool Routing
        if (circuitBreaker.isCallPermitted()) {
            long startNanos = System.nanoTime();
            resultFuture = publisher.publish(command)
                    .thenApply(ignored -> {
                        circuitBreaker.recordSuccess(System.nanoTime() - startNanos);
                        return (EdgeCommandResult) new EdgeCommandResult.Accepted(
                                command.operationId(),
                                "/operations/" + command.operationId(),
                                false
                        );
                    })
                    .exceptionallyCompose(error -> {
                        circuitBreaker.recordFailure(error);
                        return fallbackToJournal(command);
                    });
        } else {
            // Circuit Breaker OPEN -> Direct spillover to local journal
            resultFuture = fallbackToJournal(command);
        }

        // Ensure bulkhead is released when future completes
        resultFuture.whenComplete((res, err) -> bulkhead.release());

        return resultFuture;
    }

    private CompletableFuture<EdgeCommandResult> fallbackToJournal(CommandEnvelope command) {
        byte[] payloadBytes = command.payloadJson().getBytes(StandardCharsets.UTF_8);
        return journal.append(command.type(), command.operationId(), payloadBytes)
                .thenApply(record -> (EdgeCommandResult) new EdgeCommandResult.Accepted(
                        command.operationId(),
                        "/operations/" + command.operationId(),
                        true
                ))
                .exceptionally(journalError -> new EdgeCommandResult.Saturated(
                        "Spool journal saturated or unavailable: " + journalError.getMessage(),
                        5
                ));
    }
}
