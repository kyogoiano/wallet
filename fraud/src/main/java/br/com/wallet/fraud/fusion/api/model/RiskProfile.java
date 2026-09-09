package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Materialized risk profile cached in DragonflyDB/Redis for fast sub-2ms gate lookups (REQ-FUSION-006).
 */
public record RiskProfile(
    double direct,
    double graph,
    double propagated,
    double behavioral,
    double ml,
    double finalRisk,
    @NonNull FraudDecision status,
    @NonNull String primaryDriver,
    boolean degraded,
    @NonNull Instant updatedAt
) {
    public RiskProfile {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(primaryDriver, "primaryDriver cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
