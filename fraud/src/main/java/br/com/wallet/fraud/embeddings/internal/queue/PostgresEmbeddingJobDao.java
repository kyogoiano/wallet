package br.com.wallet.fraud.embeddings.internal.queue;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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

@Repository
public class PostgresEmbeddingJobDao implements EmbeddingJobRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresEmbeddingJobDao.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresEmbeddingJobDao(@NonNull final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
    }

    private static final RowMapper<EmbeddingJob> JOB_ROW_MAPPER = new RowMapper<>() {
        @Override
        public EmbeddingJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EmbeddingJob(
                rs.getObject("id", UUID.class),
                rs.getObject("entity_id", UUID.class),
                rs.getString("model_version"),
                rs.getTimestamp("as_of").toInstant(),
                EmbeddingJobStatus.valueOf(rs.getString("status")),
                rs.getObject("worker_token", UUID.class),
                toInstant(rs.getTimestamp("lease_until")),
                rs.getInt("attempt_count"),
                rs.getInt("max_attempts"),
                rs.getTimestamp("available_at").toInstant(),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("completed_at")),
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
    public boolean enqueueJob(@NonNull final UUID entityId, @NonNull final String modelVersion, @NonNull final Instant asOf) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        String sql = """
            INSERT INTO fraud_embedding_jobs (
                id, entity_id, model_version, as_of, status, attempt_count, max_attempts, available_at, created_at, updated_at
            ) VALUES (gen_random_uuid(), ?, ?, ?, 'PENDING', 0, 3, NOW(), NOW(), NOW())
            ON CONFLICT (entity_id, model_version) WHERE status IN ('PENDING', 'RUNNING', 'RETRY_WAIT')
            DO NOTHING
            """;

        try {
            int rows = jdbcTemplate.update(sql, entityId, modelVersion, Timestamp.from(asOf));
            return rows > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    @NonNull
    public Optional<EmbeddingJob> claimNextJob(@NonNull final UUID workerToken, @NonNull final Duration leaseDuration) {
        Objects.requireNonNull(workerToken, "workerToken cannot be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration cannot be null");

        String sql = """
            WITH next_job AS (
                SELECT id FROM fraud_embedding_jobs
                WHERE status IN ('PENDING', 'RETRY_WAIT')
                  AND available_at <= NOW()
                ORDER BY available_at ASC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE fraud_embedding_jobs j
            SET status = 'RUNNING',
                worker_token = ?,
                lease_until = NOW() + (? || ' milliseconds')::INTERVAL,
                attempt_count = j.attempt_count + 1,
                started_at = NOW(),
                updated_at = NOW()
            FROM next_job
            WHERE j.id = next_job.id
            RETURNING j.id, j.entity_id, j.model_version, j.as_of, j.status, j.worker_token, 
                      j.lease_until, j.attempt_count, j.max_attempts, j.available_at, 
                      j.started_at, j.completed_at, j.last_error, j.created_at, j.updated_at
            """;

        List<EmbeddingJob> jobs = jdbcTemplate.query(
            sql,
            JOB_ROW_MAPPER,
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
            UPDATE fraud_embedding_jobs
            SET status = 'COMPLETED',
                completed_at = NOW(),
                updated_at = NOW()
            WHERE id = ? AND worker_token = ? AND status = 'RUNNING'
            """;

        int rows = jdbcTemplate.update(sql, jobId, workerToken);
        return rows > 0;
    }

    @Override
    public boolean failJob(@NonNull final UUID jobId, @NonNull final UUID workerToken, @NonNull final String errorMessage, @NonNull final Duration retryBackoff) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(workerToken, "workerToken cannot be null");
        Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
        Objects.requireNonNull(retryBackoff, "retryBackoff cannot be null");

        String sql = """
            UPDATE fraud_embedding_jobs
            SET status = CASE WHEN attempt_count >= max_attempts THEN 'FAILED' ELSE 'RETRY_WAIT' END,
                available_at = NOW() + (? || ' milliseconds')::INTERVAL,
                last_error = ?,
                updated_at = NOW()
            WHERE id = ? AND worker_token = ? AND status = 'RUNNING'
            """;

        int rows = jdbcTemplate.update(sql, retryBackoff.toMillis(), errorMessage, jobId, workerToken);
        return rows > 0;
    }

    @Override
    public int reclaimExpiredLeases(@NonNull final Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        String sql = """
            UPDATE fraud_embedding_jobs
            SET status = 'RETRY_WAIT',
                available_at = NOW(),
                worker_token = NULL,
                lease_until = NULL,
                updated_at = NOW()
            WHERE status = 'RUNNING' AND lease_until < ?
            """;

        return jdbcTemplate.update(sql, Timestamp.from(now));
    }
}
