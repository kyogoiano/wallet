package br.com.wallet.unit.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.internal.propagation.PropagationJob;
import br.com.wallet.fraud.intelligence.internal.propagation.PropagationJobRepository;
import br.com.wallet.fraud.intelligence.internal.propagation.PropagationJobStatus;
import br.com.wallet.fraud.intelligence.internal.propagation.PropagationJobWorker;
import br.com.wallet.fraud.intelligence.propagation.PropagationResult;
import br.com.wallet.fraud.intelligence.propagation.RiskPropagationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PropagationJobWorker Unit Tests (Claim, Execute, Retry & Leases)")
class PropagationJobWorkerTest {

    private PropagationJobRepository jobRepository;
    private RiskPropagationEngine propagationEngine;
    private PropagationJobWorker worker;

    @BeforeEach
    void setUp() {
        jobRepository = Mockito.mock(PropagationJobRepository.class);
        propagationEngine = Mockito.mock(RiskPropagationEngine.class);
        worker = new PropagationJobWorker(jobRepository, propagationEngine);
    }

    @Test
    @DisplayName("Should return false when job queue is empty")
    void shouldReturnFalseWhenQueueIsEmpty() {
        when(jobRepository.claimNext(eq(worker.getWorkerToken()), any(Duration.class)))
            .thenReturn(Optional.empty());

        boolean processed = worker.pollAndExecuteNext();
        assertThat(processed).isFalse();
    }

    @Test
    @DisplayName("Should claim job, evaluate propagation, and mark as completed")
    void shouldClaimAndCompleteJob() {
        UUID entityId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant asOf = Instant.now();

        PropagationJob job = new PropagationJob(
            jobId,
            entityId,
            PropagationJobStatus.RUNNING,
            asOf,
            worker.getWorkerToken(),
            asOf.plus(Duration.ofMinutes(2)),
            1,
            asOf,
            asOf,
            null,
            null,
            "v1",
            asOf,
            asOf
        );

        when(jobRepository.claimNext(eq(worker.getWorkerToken()), any(Duration.class)))
            .thenReturn(Optional.of(job));
        when(propagationEngine.evaluateEntity(entityId, asOf))
            .thenReturn(new PropagationResult(entityId, asOf, Collections.emptyMap(), 0, "v1"));
        when(jobRepository.complete(jobId, worker.getWorkerToken())).thenReturn(true);

        boolean processed = worker.pollAndExecuteNext();

        assertThat(processed).isTrue();
        verify(propagationEngine).evaluateEntity(entityId, asOf);
        verify(jobRepository).complete(jobId, worker.getWorkerToken());
    }

    @Test
    @DisplayName("Should schedule retry with backoff on failure")
    void shouldScheduleRetryOnFailure() {
        UUID entityId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant asOf = Instant.now();

        PropagationJob job = new PropagationJob(
            jobId,
            entityId,
            PropagationJobStatus.RUNNING,
            asOf,
            worker.getWorkerToken(),
            asOf.plus(Duration.ofMinutes(2)),
            1,
            asOf,
            asOf,
            null,
            null,
            "v1",
            asOf,
            asOf
        );

        when(jobRepository.claimNext(eq(worker.getWorkerToken()), any(Duration.class)))
            .thenReturn(Optional.of(job));
        when(propagationEngine.evaluateEntity(entityId, asOf))
            .thenThrow(new RuntimeException("Database timeout"));

        boolean processed = worker.pollAndExecuteNext();

        assertThat(processed).isTrue();
        verify(jobRepository).scheduleRetry(eq(jobId), eq(worker.getWorkerToken()), eq("Database timeout"), any(Duration.class));
    }
}
