package br.com.wallet.unit.fraud.embeddings;

import br.com.wallet.fraud.embeddings.api.BehavioralEmbeddingEngine;
import br.com.wallet.fraud.embeddings.internal.queue.EmbeddingJob;
import br.com.wallet.fraud.embeddings.internal.queue.EmbeddingJobRepository;
import br.com.wallet.fraud.embeddings.internal.queue.EmbeddingJobStatus;
import br.com.wallet.fraud.embeddings.internal.queue.EmbeddingJobWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingJobWorker Unit Tests (Worker Token Lease & Asynchronous Lifecycle)")
class EmbeddingJobWorkerTest {

    @Mock
    private EmbeddingJobRepository jobRepository;

    @Mock
    private BehavioralEmbeddingEngine embeddingEngine;

    private EmbeddingJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new EmbeddingJobWorker(jobRepository, embeddingEngine);
    }

    @Test
    @DisplayName("I-VEC-007: Should claim and complete embedding evaluation job successfully")
    void shouldProcessJobSuccessfully() {
        UUID jobId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID workerToken = UUID.randomUUID();

        EmbeddingJob job = new EmbeddingJob(
            jobId, entityId, "v1", Instant.now(), EmbeddingJobStatus.RUNNING,
            workerToken, Instant.now().plusSeconds(30), 1, 3,
            Instant.now(), Instant.now(), null, null, Instant.now(), Instant.now()
        );

        when(jobRepository.claimNextJob(any(), any())).thenReturn(Optional.of(job));
        when(jobRepository.completeJob(eq(jobId), any())).thenReturn(true);

        boolean processed = worker.processNextJob();

        assertThat(processed).isTrue();
        verify(embeddingEngine).evaluateAndPersistRisk(entityId);
        verify(jobRepository).completeJob(eq(jobId), any());
    }

    @Test
    @DisplayName("REQ-VEC-011: Should fail and schedule retry when evaluation throws exception")
    void shouldFailJobOnException() {
        UUID jobId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        EmbeddingJob job = new EmbeddingJob(
            jobId, entityId, "v1", Instant.now(), EmbeddingJobStatus.RUNNING,
            UUID.randomUUID(), Instant.now().plusSeconds(30), 1, 3,
            Instant.now(), Instant.now(), null, null, Instant.now(), Instant.now()
        );

        when(jobRepository.claimNextJob(any(), any())).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("Database timeout")).when(embeddingEngine).evaluateAndPersistRisk(entityId);

        boolean processed = worker.processNextJob();

        assertThat(processed).isFalse();
        verify(jobRepository).failJob(eq(jobId), any(), eq("Database timeout"), any(Duration.class));
    }

    @Test
    @DisplayName("REQ-VEC-011: Should return false when no jobs are available to claim")
    void shouldReturnFalseWhenNoJobs() {
        when(jobRepository.claimNextJob(any(), any())).thenReturn(Optional.empty());

        boolean processed = worker.processNextJob();

        assertThat(processed).isFalse();
    }
}
