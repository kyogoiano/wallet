package br.com.wallet.fraud.intelligence.internal.propagation;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access interface for durable propagation jobs.
 */
public interface PropagationJobRepository {

    /**
     * Enqueues a new propagation job with status PENDING.
     * If an active job (PENDING, RUNNING, RETRY_WAIT) already exists for (entity_id, model_version), returns empty.
     */
    @NonNull
    Optional<UUID> enqueue(@NonNull UUID entityId, @NonNull Instant asOf, @NonNull String modelVersion);

    /**
     * Atomically claims the next eligible job using SELECT FOR UPDATE SKIP LOCKED.
     */
    @NonNull
    Optional<PropagationJob> claimNext(@NonNull UUID workerToken, @NonNull Duration leaseDuration);

    /**
     * Renews the lease timestamp for an active job.
     */
    boolean renewLease(@NonNull UUID jobId, @NonNull UUID workerToken, @NonNull Duration extension);

    /**
     * Marks a job as COMPLETED.
     */
    boolean complete(@NonNull UUID jobId, @NonNull UUID workerToken);

    /**
     * Schedules a retry with backoff for a job.
     */
    boolean scheduleRetry(@NonNull UUID jobId, @NonNull UUID workerToken, @NonNull String lastError, @NonNull Duration retryDelay);

    /**
     * Marks a job as FAILED when max retries are exhausted.
     */
    boolean fail(@NonNull UUID jobId, @NonNull UUID workerToken, @NonNull String lastError);

    /**
     * Reclaims expired leases (RUNNING with lease_until < now()) back to RETRY_WAIT.
     */
    int reapExpiredLeases();

    /**
     * Finds a job by ID.
     */
    @NonNull
    Optional<PropagationJob> findById(@NonNull UUID jobId);
}
