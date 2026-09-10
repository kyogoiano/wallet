package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.EdgeCommandResult;
import br.com.wallet.edge.internal.command.CommandAcceptanceService;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.ingress.EdgeRequestValidator;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.spi.DurableSpilloverJournal;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.internal.resilience.BrokerCircuitBreaker;
import br.com.wallet.edge.internal.resilience.IngressBulkhead;
import br.com.wallet.edge.internal.resilience.PerimeterRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("CommandAcceptanceService Unit Tests (TASK-3.1, REQ-EDG-001, REQ-EDG-002, REQ-EDG-015, I-EDGE-001)")
class CommandAcceptanceServiceTest {

    private EdgeCommandPublisher publisher;
    private DurableSpilloverJournal journal;
    private PerimeterRateLimiter rateLimiter;
    private IngressBulkhead bulkhead;
    private BrokerCircuitBreaker circuitBreaker;
    private EdgeRequestValidator validator;
    private CommandAcceptanceService service;

    @BeforeEach
    void setUp() {
        publisher = mock(EdgeCommandPublisher.class);
        journal = mock(DurableSpilloverJournal.class);
        rateLimiter = mock(PerimeterRateLimiter.class);
        bulkhead = mock(IngressBulkhead.class);
        circuitBreaker = mock(BrokerCircuitBreaker.class);
        validator = new EdgeRequestValidator(64 * 1024); // 64KB limit

        // Defaults: pass rate limiter and bulkhead
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        when(bulkhead.tryAcquire()).thenReturn(true);
        when(circuitBreaker.isCallPermitted()).thenReturn(true);

        service = new CommandAcceptanceService(
                publisher,
                journal,
                rateLimiter,
                bulkhead,
                circuitBreaker,
                validator
        );
    }

    private CommandEnvelope sampleEnvelope(UUID opId, String payload) {
        return CommandEnvelope.create(opId, CommandType.TRANSFER, payload, "192.168.1.100");
    }

    @Test
    @DisplayName("Should accept and return 202 when primary broker succeeds (REQ-EDG-001, I-EDGE-001)")
    void shouldAcceptOnBrokerSuccess() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope envelope = sampleEnvelope(opId, "{\"amount\": 100.00}");

        when(publisher.publish(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        EdgeCommandResult result = service.acceptCommand(envelope).get(1, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.Accepted.class);
        EdgeCommandResult.Accepted accepted = (EdgeCommandResult.Accepted) result;
        assertThat(accepted.operationId()).isEqualTo(opId);
        assertThat(accepted.spooledDegraded()).isFalse();
        assertThat(accepted.location()).isEqualTo("/operations/" + opId);

        verify(publisher).publish(envelope);
        verifyNoInteractions(journal);
        verify(bulkhead).release();
    }

    @Test
    @DisplayName("Should spill over to local journal when broker circuit breaker is OPEN (REQ-EDG-002, I-EDGE-001)")
    void shouldSpilloverWhenCircuitBreakerOpen() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope envelope = sampleEnvelope(opId, "{\"amount\": 250.00}");

        when(circuitBreaker.isCallPermitted()).thenReturn(false);
        JournalRecord spooled = new JournalRecord(1L, opId, System.currentTimeMillis(), CommandType.TRANSFER, envelope.payloadJson().getBytes());
        when(journal.append(eq(CommandType.TRANSFER), eq(opId), any())).thenReturn(CompletableFuture.completedFuture(spooled));

        EdgeCommandResult result = service.acceptCommand(envelope).get(1, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.Accepted.class);
        EdgeCommandResult.Accepted accepted = (EdgeCommandResult.Accepted) result;
        assertThat(accepted.operationId()).isEqualTo(opId);
        assertThat(accepted.spooledDegraded()).isTrue();

        verifyNoInteractions(publisher);
        verify(journal).append(eq(CommandType.TRANSFER), eq(opId), any());
        verify(bulkhead).release();
    }

    @Test
    @DisplayName("Should spill over to local journal when broker publish fails (REQ-EDG-002)")
    void shouldSpilloverWhenBrokerPublishFails() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope envelope = sampleEnvelope(opId, "{\"amount\": 350.00}");

        CompletableFuture<Void> failedBrokerFuture = new CompletableFuture<>();
        failedBrokerFuture.completeExceptionally(new RuntimeException("NATS broker unavailable"));
        when(publisher.publish(envelope)).thenReturn(failedBrokerFuture);

        JournalRecord spooled = new JournalRecord(1L, opId, System.currentTimeMillis(), CommandType.TRANSFER, envelope.payloadJson().getBytes());
        when(journal.append(eq(CommandType.TRANSFER), eq(opId), any())).thenReturn(CompletableFuture.completedFuture(spooled));

        EdgeCommandResult result = service.acceptCommand(envelope).get(1, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.Accepted.class);
        EdgeCommandResult.Accepted accepted = (EdgeCommandResult.Accepted) result;
        assertThat(accepted.operationId()).isEqualTo(opId);
        assertThat(accepted.spooledDegraded()).isTrue();

        verify(publisher).publish(envelope);
        verify(circuitBreaker).recordFailure(any());
        verify(journal).append(eq(CommandType.TRANSFER), eq(opId), any());
        verify(bulkhead).release();
    }

    @Test
    @DisplayName("Should reject with PayloadTooLarge when payload exceeds 64KB (REQ-EDG-015)")
    void shouldRejectWhenPayloadExceeds64Kb() throws Exception {
        UUID opId = UUID.randomUUID();
        String oversized = "x".repeat(65 * 1024); // 65KB
        CommandEnvelope envelope = sampleEnvelope(opId, oversized);

        EdgeCommandResult result = service.acceptCommand(envelope).get(1, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.ContentTooLarge.class);
        EdgeCommandResult.ContentTooLarge contentTooLarge = (EdgeCommandResult.ContentTooLarge) result;
        assertThat(contentTooLarge.payloadBytes()).isGreaterThan(64 * 1024);

        verifyNoInteractions(publisher);
        verifyNoInteractions(journal);
        verifyNoInteractions(bulkhead); // Rejected before bulkhead acquisition
    }

    @Test
    @DisplayName("Should reject with RateLimited when perimeter rate limiter denies token (REQ-EDG-004)")
    void shouldRejectWhenRateLimited() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope envelope = sampleEnvelope(opId, "{\"amount\": 50.00}");

        when(rateLimiter.tryAcquire(any())).thenReturn(false);
        when(rateLimiter.getRetryAfterSeconds()).thenReturn(1);

        EdgeCommandResult result = service.acceptCommand(envelope).get(1, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.RateLimited.class);
        EdgeCommandResult.RateLimited rateLimited = (EdgeCommandResult.RateLimited) result;
        assertThat(rateLimited.retryAfterSeconds()).isEqualTo(1);

        verifyNoInteractions(publisher);
        verifyNoInteractions(journal);
        verifyNoInteractions(bulkhead);
    }

    @Test
    @DisplayName("Should reject with BulkheadFull when max inflight concurrency is exceeded (REQ-EDG-005, I-EDGE-002)")
    void shouldRejectWhenBulkheadFull() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope envelope = sampleEnvelope(opId, "{\"amount\": 50.00}");

        when(bulkhead.tryAcquire()).thenReturn(false);
        when(bulkhead.getCurrentInflight()).thenReturn(2048);
        when(bulkhead.getMaxInflight()).thenReturn(2048);

        EdgeCommandResult result = service.acceptCommand(envelope).get(1, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.BulkheadFull.class);
        EdgeCommandResult.BulkheadFull bulkheadFull = (EdgeCommandResult.BulkheadFull) result;
        assertThat(bulkheadFull.currentInflight()).isEqualTo(2048);

        verifyNoInteractions(publisher);
        verifyNoInteractions(journal);
    }

    @Test
    @DisplayName("Should return Saturated when both broker and spool journal fail (REQ-EDG-003)")
    void shouldReturnSaturatedWhenBrokerAndJournalFail() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope envelope = sampleEnvelope(opId, "{\"amount\": 50.00}");

        CompletableFuture<Void> failedBroker = new CompletableFuture<>();
        failedBroker.completeExceptionally(new RuntimeException("Broker down"));
        when(publisher.publish(envelope)).thenReturn(failedBroker);

        CompletableFuture<JournalRecord> failedJournal = new CompletableFuture<>();
        failedJournal.completeExceptionally(new IllegalStateException("Spool journal is SATURATED"));
        when(journal.append(any(), any(), any())).thenReturn(failedJournal);

        EdgeCommandResult result = service.acceptCommand(envelope).get(1, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.Saturated.class);
        EdgeCommandResult.Saturated sat = (EdgeCommandResult.Saturated) result;
        assertThat(sat.retryAfterSeconds()).isEqualTo(5);

        verify(bulkhead).release();
    }

    @Test
    @DisplayName("Should detect idempotent replay for same operationId and return Accepted immediately (TASK-3.7)")
    void shouldAcceptIdempotentReplay() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope first = sampleEnvelope(opId, "{\"from\":\"A\",\"to\":\"B\",\"amount\":50.00}");
        CommandEnvelope replay = sampleEnvelope(opId, "{\"amount\":50.00,\"to\":\"B\",\"from\":\"A\"}");

        when(publisher.publish(first)).thenReturn(CompletableFuture.completedFuture(null));

        EdgeCommandResult firstResult = service.acceptCommand(first).get(1, TimeUnit.SECONDS);
        assertThat(firstResult).isInstanceOf(EdgeCommandResult.Accepted.class);

        EdgeCommandResult replayResult = service.acceptCommand(replay).get(1, TimeUnit.SECONDS);
        assertThat(replayResult).isInstanceOf(EdgeCommandResult.Accepted.class);

        // Publisher should only be called once, for the first command
        verify(publisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("Should return Conflict when operationId is reused with different payload (TASK-3.7, TASK-3.8)")
    void shouldRejectConflictingPayload() throws Exception {
        UUID opId = UUID.randomUUID();
        CommandEnvelope first = sampleEnvelope(opId, "{\"amount\":50.00}");
        CommandEnvelope conflict = sampleEnvelope(opId, "{\"amount\":150.00}");

        when(publisher.publish(first)).thenReturn(CompletableFuture.completedFuture(null));

        EdgeCommandResult firstResult = service.acceptCommand(first).get(1, TimeUnit.SECONDS);
        assertThat(firstResult).isInstanceOf(EdgeCommandResult.Accepted.class);

        EdgeCommandResult conflictResult = service.acceptCommand(conflict).get(1, TimeUnit.SECONDS);
        assertThat(conflictResult).isInstanceOf(EdgeCommandResult.Conflict.class);
    }
}
