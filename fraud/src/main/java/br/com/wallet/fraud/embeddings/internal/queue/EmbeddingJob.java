package br.com.wallet.fraud.embeddings.internal.queue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EmbeddingJob(
    @NonNull UUID id,
    @NonNull UUID entityId,
    @NonNull String modelVersion,
    @NonNull Instant asOf,
    @NonNull EmbeddingJobStatus status,
    @Nullable UUID workerToken,
    @Nullable Instant leaseUntil,
    int attemptCount,
    int maxAttempts,
    @NonNull Instant availableAt,
    @Nullable Instant startedAt,
    @Nullable Instant completedAt,
    @Nullable String lastError,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public EmbeddingJob {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(availableAt, "availableAt cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
