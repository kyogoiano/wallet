package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Request payload for recording human compliance analyst decisions (REQ-FUSION-008).
 */
public record AnalystReviewRequest(
    @NonNull String analystId,
    @NonNull String verdict,
    @Nullable String notes
) {
    public AnalystReviewRequest {
        Objects.requireNonNull(analystId, "analystId cannot be null");
        Objects.requireNonNull(verdict, "verdict cannot be null");
    }
}
