package br.com.wallet.fraud.intelligence.internal.propagation;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates a durable risk propagation execution task.
 */
public record PropagationJob(
    @NonNull UUID id,
    @NonNull UUID entityId,
    @NonNull PropagationJobStatus status,
    @NonNull Instant asOf,
    @Nullable UUID workerToken,
    @Nullable Instant leaseUntil,
    int attemptCount,
    @NonNull Instant availableAt,
    @Nullable Instant startedAt,
    @Nullable Instant completedAt,
    @Nullable String lastError,
    @NonNull String modelVersion,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public PropagationJob {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(availableAt, "availableAt cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public static PropagationJob createPending(@NonNull UUID entityId, @NonNull Instant asOf, @NonNull String modelVersion) {
        Instant now = Instant.now();
        return new PropagationJob(
            UUID.randomUUID(),
            entityId,
            PropagationJobStatus.PENDING,
            asOf,
            null,
            null,
            0,
            now,
            null,
            null,
            null,
            modelVersion,
            now,
            now
        );
    }
}
