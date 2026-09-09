package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable entity for human-in-the-loop compliance investigation checkpoints (REQ-FUSION-008).
 */
public record CheckpointRecord(
    @NonNull UUID checkpointId,
    @NonNull UUID entityId,
    @NonNull String status,
    @NonNull String statePayload,
    double finalRisk,
    @NonNull String riskClassification,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt
) {
    public CheckpointRecord {
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(statePayload, "statePayload cannot be null");
        Objects.requireNonNull(riskClassification, "riskClassification cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
