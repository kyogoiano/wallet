package br.com.wallet.fraud.fusion.internal.persistence;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of the durable job queue with partial unique index coalescing (REQ-FUSION-004, REQ-FUSION-009).
 */
@Repository
public class PostgresFusionJobDao implements FusionJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresFusionJobDao(@NonNull final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
    }

    private static final RowMapper<FusionJob> ROW_MAPPER = new RowMapper<>() {
        @Override
        public FusionJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FusionJob(
                rs.getObject("job_id", UUID.class),
                rs.getObject("entity_id", UUID.class),
                rs.getString("model_version"),
                FusionJobStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("as_of").toInstant(),
                rs.getInt("attempt_count"),
                rs.getObject("worker_token", UUID.class),
                toInstant(rs.getTimestamp("lease_expires_at")),
                toInstant(rs.getTimestamp("next_attempt_at")),
                rs.getString("idempotency_key"),
                rs.getString("payload"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }

        private Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    };

    @Override
    @NonNull
    public UUID enqueueOrCoalesceJob(
        @NonNull final UUID entityId,
        @NonNull final String modelVersion,
        @NonNull final Instant asOf,
        @Nullable final String payload
    ) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        String sql = """
            INSERT INTO fraud_fusion_jobs (
                job_id, entity_id, model_version, status, as_of, attempt_count, payload, created_at, updated_at
            ) VALUES (gen_random_uuid(), ?, ?, 'PENDING', ?, 0, ?::jsonb, NOW(), NOW())
            ON CONFLICT (entity_id) WHERE status = 'PENDING'
            DO UPDATE SET
                as_of = GREATEST(fraud_fusion_jobs.as_of, EXCLUDED.as_of),
                payload = COALESCE(EXCLUDED.payload, fraud_fusion_jobs.payload),
                updated_at = NOW()
            RETURNING job_id;
            """;

        String jsonPayload = payload != null ? payload : "{}";
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
            sql,
            UUID.class,
            entityId,
            modelVersion,
            Timestamp.from(asOf),
            jsonPayload
        ));
    }

    @Override
    @NonNull
    public Optional<FusionJob> claimNextJob(@NonNull final UUID workerToken, @NonNull final Duration leaseDuration) {
        Objects.requireNonNull(workerToken, "workerToken cannot be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration cannot be null");

        String sql = """
            WITH next_job AS (
                SELECT job_id FROM fraud_fusion_jobs
                WHERE status IN ('PENDING', 'RETRY_WAIT')
                  AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
                ORDER BY as_of ASC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE fraud_fusion_jobs j
            SET status = 'RUNNING',
                worker_token = ?,
                lease_expires_at = NOW() + (? || ' milliseconds')::INTERVAL,
                attempt_count = j.attempt_count + 1,
                updated_at = NOW()
            FROM next_job
            WHERE j.job_id = next_job.job_id
            RETURNING j.job_id, j.entity_id, j.model_version, j.status, j.as_of,
                      j.attempt_count, j.worker_token, j.lease_expires_at, j.next_attempt_at,
                      j.idempotency_key, j.payload, j.last_error, j.created_at, j.updated_at;
            """;

        List<FusionJob> jobs = jdbcTemplate.query(
            sql,
            ROW_MAPPER,
            workerToken,
            leaseDuration.toMillis()
        );

        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.getFirst());
    }

    @Override
    public boolean completeJob(@NonNull final UUID jobId, @NonNull final UUID workerToken) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(workerToken, "workerToken cannot be null");

        String sql = """
            UPDATE fraud_fusion_jobs
            SET status = 'COMPLETED',
                worker_token = NULL,
                lease_expires_at = NULL,
                updated_at = NOW()
            WHERE job_id = ? AND worker_token = ? AND status = 'RUNNING'
            """;

        return jdbcTemplate.update(sql, jobId, workerToken) > 0;
    }

    @Override
    public boolean rescheduleJob(
        @NonNull final UUID jobId,
        @NonNull final Duration retryBackoff,
        @NonNull final String errorMessage
    ) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(retryBackoff, "retryBackoff cannot be null");
        Objects.requireNonNull(errorMessage, "errorMessage cannot be null");

        String sql = """
            UPDATE fraud_fusion_jobs
            SET status = 'RETRY_WAIT',
                next_attempt_at = NOW() + (? || ' milliseconds')::INTERVAL,
                worker_token = NULL,
                lease_expires_at = NULL,
                last_error = ?,
                updated_at = NOW()
            WHERE job_id = ?
            """;

        return jdbcTemplate.update(sql, retryBackoff.toMillis(), errorMessage, jobId) > 0;
    }

    @Override
    public boolean markJobFailed(@NonNull final UUID jobId, @NonNull final String errorMessage) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(errorMessage, "errorMessage cannot be null");

        String sql = """
            UPDATE fraud_fusion_jobs
            SET status = 'FAILED',
                worker_token = NULL,
                lease_expires_at = NULL,
                last_error = ?,
                updated_at = NOW()
            WHERE job_id = ?
            """;

        return jdbcTemplate.update(sql, errorMessage, jobId) > 0;
    }

    @Override
    @NonNull
    public List<FusionJob> findExpiredRunningJobs(@NonNull final Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        String sql = """
            SELECT job_id, entity_id, model_version, status, as_of, attempt_count, worker_token,
                   lease_expires_at, next_attempt_at, idempotency_key, payload, last_error,
                   created_at, updated_at
            FROM fraud_fusion_jobs
            WHERE status = 'RUNNING' AND lease_expires_at < ?
            ORDER BY lease_expires_at ASC
            """;

        return jdbcTemplate.query(sql, ROW_MAPPER, Timestamp.from(now));
    }

    @Override
    @NonNull
    public Optional<FusionJob> findById(@NonNull final UUID jobId) {
        Objects.requireNonNull(jobId, "jobId cannot be null");

        String sql = """
            SELECT job_id, entity_id, model_version, status, as_of, attempt_count, worker_token,
                   lease_expires_at, next_attempt_at, idempotency_key, payload, last_error,
                   created_at, updated_at
            FROM fraud_fusion_jobs
            WHERE job_id = ?
            """;

        List<FusionJob> jobs = jdbcTemplate.query(sql, ROW_MAPPER, jobId);
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.getFirst());
    }

    @Override
    @NonNull
    public List<FusionJob> findByEntityId(@NonNull final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        String sql = """
            SELECT job_id, entity_id, model_version, status, as_of, attempt_count, worker_token,
                   lease_expires_at, next_attempt_at, idempotency_key, payload, last_error,
                   created_at, updated_at
            FROM fraud_fusion_jobs
            WHERE entity_id = ?
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, ROW_MAPPER, entityId);
    }
}
