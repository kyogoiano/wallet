package br.com.wallet.fraud.embeddings.internal.queue;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmbeddingJobRepository {

    boolean enqueueJob(@NonNull UUID entityId, @NonNull String modelVersion, @NonNull Instant asOf);

    @NonNull
    Optional<EmbeddingJob> claimNextJob(@NonNull UUID workerToken, @NonNull Duration leaseDuration);

    boolean completeJob(@NonNull UUID jobId, @NonNull UUID workerToken);

    boolean failJob(@NonNull UUID jobId, @NonNull UUID workerToken, @NonNull String errorMessage, @NonNull Duration retryBackoff);

    int reclaimExpiredLeases(@NonNull Instant now);
}
