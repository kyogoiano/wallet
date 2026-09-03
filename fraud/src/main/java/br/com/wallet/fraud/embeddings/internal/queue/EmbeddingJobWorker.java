package br.com.wallet.fraud.embeddings.internal.queue;

import br.com.wallet.fraud.embeddings.api.BehavioralEmbeddingEngine;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Background worker that claims and processes embedding evaluation jobs using worker token leases.
 */
@Component
public class EmbeddingJobWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingJobWorker.class);
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofSeconds(10);

    private final EmbeddingJobRepository jobRepository;
    private final BehavioralEmbeddingEngine embeddingEngine;

    public EmbeddingJobWorker(
        @NonNull final EmbeddingJobRepository jobRepository,
        @NonNull final BehavioralEmbeddingEngine embeddingEngine
    ) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository cannot be null");
        this.embeddingEngine = Objects.requireNonNull(embeddingEngine, "embeddingEngine cannot be null");
    }

    public boolean processNextJob() {
        UUID workerToken = UUID.randomUUID();
        Optional<EmbeddingJob> maybeJob = jobRepository.claimNextJob(workerToken, DEFAULT_LEASE_DURATION);

        if (maybeJob.isEmpty()) {
            return false;
        }

        EmbeddingJob job = maybeJob.get();
        log.info("Processing embedding evaluation job: {} for entity: {}", job.id(), job.entityId());

        try {
            embeddingEngine.evaluateAndPersistRisk(job.entityId());
            boolean completed = jobRepository.completeJob(job.id(), workerToken);
            log.info("Successfully completed embedding job: {} (completed={})", job.id(), completed);
            return true;
        } catch (Exception e) {
            log.error("Failed processing embedding job: {} - {}", job.id(), e.getMessage(), e);
            jobRepository.failJob(job.id(), workerToken, e.getMessage() != null ? e.getMessage() : "Unknown error", DEFAULT_RETRY_BACKOFF);
            return false;
        }
    }

    public int reclaimExpiredLeases() {
        return jobRepository.reclaimExpiredLeases(Instant.now());
    }
}
