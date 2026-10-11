package br.com.wallet.fraud.fusion.internal.persistence;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable entity representing a durable background evaluation job.
 */
public record FusionJob(
    @NonNull UUID jobId,
    @NonNull UUID entityId,
    @NonNull String modelVersion,
    @NonNull FusionJobStatus status,
    @NonNull Instant asOf,
    int attemptCount,
    @Nullable UUID workerToken,
    @Nullable Instant leaseExpiresAt,
    @Nullable Instant nextAttemptAt,
    @Nullable String idempotencyKey,
    @Nullable String payload,
    @Nullable String lastError,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public FusionJob {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
