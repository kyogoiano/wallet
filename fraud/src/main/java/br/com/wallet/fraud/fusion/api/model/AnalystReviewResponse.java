package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Response payload confirming recorded human analyst review (REQ-FUSION-008).
 */
public record AnalystReviewResponse(
    @NonNull UUID reviewId,
    @NonNull UUID checkpointId,
    @NonNull UUID entityId,
    @NonNull String status,
    @NonNull String verdict
) {
    public AnalystReviewResponse {
        Objects.requireNonNull(reviewId, "reviewId cannot be null");
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(verdict, "verdict cannot be null");
    }
}
