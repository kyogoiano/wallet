package br.com.wallet.fraud.fusion.internal.persistence;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for the durable PostgreSQL fusion job queue.
 */
public interface FusionJobRepository {

    @NonNull
    UUID enqueueOrCoalesceJob(
        @NonNull UUID entityId,
        @NonNull String modelVersion,
        @NonNull Instant asOf,
        @Nullable String payload
    );

    @NonNull
    Optional<FusionJob> claimNextJob(@NonNull UUID workerToken, @NonNull Duration leaseDuration);

    boolean completeJob(@NonNull UUID jobId, @NonNull UUID workerToken);

    boolean rescheduleJob(@NonNull UUID jobId, @NonNull Duration retryBackoff, @NonNull String errorMessage);

    boolean markJobFailed(@NonNull UUID jobId, @NonNull String errorMessage);

    @NonNull
    List<FusionJob> findExpiredRunningJobs(@NonNull Instant now);

    @NonNull
    Optional<FusionJob> findById(@NonNull UUID jobId);

    @NonNull
    List<FusionJob> findByEntityId(@NonNull UUID entityId);
}
