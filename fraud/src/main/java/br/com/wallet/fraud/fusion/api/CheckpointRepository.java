package br.com.wallet.fraud.fusion.api;

import br.com.wallet.fraud.fusion.api.model.CheckpointRecord;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Public persistence contract for compliance checkpoints and human review logs (REQ-FUSION-008).
 */
public interface CheckpointRepository {

    @NonNull
    UUID saveCheckpoint(
        @NonNull UUID entityId,
        @NonNull String statePayload,
        double finalRisk,
        @NonNull String riskClassification
    );

    @NonNull
    UUID saveReview(
        @NonNull UUID checkpointId,
        @NonNull UUID entityId,
        @NonNull String analystId,
        @NonNull String verdict,
        @Nullable String notes
    );

    @NonNull
    Optional<CheckpointRecord> findCheckpointById(@NonNull UUID checkpointId);
}
