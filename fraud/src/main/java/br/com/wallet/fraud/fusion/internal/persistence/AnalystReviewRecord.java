package br.com.wallet.fraud.fusion.internal.persistence;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit entity for human-in-the-loop analyst decisions (REQ-FUSION-008).
 */
public record AnalystReviewRecord(
    @NonNull UUID reviewId,
    @NonNull UUID checkpointId,
    @NonNull UUID entityId,
    @NonNull String analystId,
    @NonNull String verdict,
    @Nullable String notes,
    @NonNull Instant reviewedAt
) {
    public AnalystReviewRecord {
        Objects.requireNonNull(reviewId, "reviewId cannot be null");
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(analystId, "analystId cannot be null");
        Objects.requireNonNull(verdict, "verdict cannot be null");
        Objects.requireNonNull(reviewedAt, "reviewedAt cannot be null");
    }
}
