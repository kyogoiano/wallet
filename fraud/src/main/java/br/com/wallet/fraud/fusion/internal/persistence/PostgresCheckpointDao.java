package br.com.wallet.fraud.fusion.internal.persistence;

import br.com.wallet.fraud.fusion.api.CheckpointRepository;
import br.com.wallet.fraud.fusion.api.model.CheckpointRecord;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation for checkpoints and human analyst reviews (REQ-FUSION-008).
 */
@Repository
public class PostgresCheckpointDao implements CheckpointRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresCheckpointDao(@NonNull final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
    }

    private static final RowMapper<CheckpointRecord> CHECKPOINT_ROW_MAPPER = (rs, rowNum) -> new CheckpointRecord(
        rs.getObject("checkpoint_id", UUID.class),
        rs.getObject("entity_id", UUID.class),
        rs.getString("status"),
        rs.getString("state_payload"),
        rs.getDouble("final_risk"),
        rs.getString("risk_classification"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    @NonNull
    public UUID saveCheckpoint(
        @NonNull final UUID entityId,
        @NonNull final String statePayload,
        final double finalRisk,
        @NonNull final String riskClassification
    ) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(statePayload, "statePayload cannot be null");
        Objects.requireNonNull(riskClassification, "riskClassification cannot be null");

        String sql = """
            INSERT INTO fraud_investigation_checkpoints (
                checkpoint_id, entity_id, status, state_payload, final_risk, risk_classification, created_at, updated_at
            ) VALUES (gen_random_uuid(), ?, 'PENDING_ANALYST', ?::jsonb, ?, ?, NOW(), NOW())
            RETURNING checkpoint_id;
            """;

        return Objects.requireNonNull(jdbcTemplate.queryForObject(
            sql,
            UUID.class,
            entityId,
            statePayload,
            finalRisk,
            riskClassification
        ));
    }

    @Override
    @Transactional
    @NonNull
    public UUID saveReview(
        @NonNull final UUID checkpointId,
        @NonNull final UUID entityId,
        @NonNull final String analystId,
        @NonNull final String verdict,
        @Nullable final String notes
    ) {
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(analystId, "analystId cannot be null");
        Objects.requireNonNull(verdict, "verdict cannot be null");

        String insertReviewSql = """
            INSERT INTO fraud_analyst_reviews (
                review_id, checkpoint_id, entity_id, analyst_id, verdict, notes, reviewed_at
            ) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, NOW())
            RETURNING review_id;
            """;

        UUID reviewId = Objects.requireNonNull(jdbcTemplate.queryForObject(
            insertReviewSql,
            UUID.class,
            checkpointId,
            entityId,
            analystId,
            verdict,
            notes
        ));

        String updateCheckpointSql = """
            UPDATE fraud_investigation_checkpoints
            SET status = 'ANALYST_REVIEWED',
                updated_at = NOW()
            WHERE checkpoint_id = ?;
            """;

        jdbcTemplate.update(updateCheckpointSql, checkpointId);

        return reviewId;
    }

    @Override
    @NonNull
    public Optional<CheckpointRecord> findCheckpointById(@NonNull final UUID checkpointId) {
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");

        String sql = """
            SELECT checkpoint_id, entity_id, status, state_payload, final_risk, risk_classification, created_at, updated_at
            FROM fraud_investigation_checkpoints
            WHERE checkpoint_id = ?
            """;

        List<CheckpointRecord> list = jdbcTemplate.query(sql, CHECKPOINT_ROW_MAPPER, checkpointId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }
}
