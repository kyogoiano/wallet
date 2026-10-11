package br.com.wallet.fraud.intelligence.internal.propagation;

import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
 * PostgreSQL implementation of PropagationJobRepository using FOR UPDATE SKIP LOCKED.
 */
@Repository
public class PostgresPropagationJobDao implements PropagationJobRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<PropagationJob> rowMapper = this::mapRow;

    public PostgresPropagationJobDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    @Override
    public @NonNull Optional<UUID> enqueue(@NonNull UUID entityId, @NonNull Instant asOf, @NonNull String modelVersion) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");

        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();

        try {
            int rows = jdbc.update("""
                INSERT INTO fraud_propagation_jobs (
                    id, entity_id, status, as_of, worker_token, lease_until,
                    attempt_count, available_at, started_at, completed_at,
                    last_error, model_version, created_at, updated_at
                )
                VALUES (?, ?, 'PENDING', ?, NULL, NULL, 0, ?, NULL, NULL, NULL, ?, ?, ?)
                ON CONFLICT (entity_id, model_version) WHERE status IN ('PENDING', 'RUNNING', 'RETRY_WAIT') DO NOTHING
                """,
                jobId,
                entityId,
                Timestamp.from(asOf),
                Timestamp.from(now),
                modelVersion,
                Timestamp.from(now),
                Timestamp.from(now)
            );

            if (rows > 0) {
                return Optional.of(jobId);
            } else {
                return Optional.empty();
            }
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public @NonNull Optional<PropagationJob> claimNext(@NonNull UUID workerToken, @NonNull Duration leaseDuration) {
        Objects.requireNonNull(workerToken, "workerToken cannot be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration cannot be null");

        Instant now = Instant.now();
        Instant leaseUntil = now.plus(leaseDuration);

        List<PropagationJob> jobs = jdbc.query("""
            WITH next_job AS (
                SELECT id
                FROM fraud_propagation_jobs
                WHERE status IN ('PENDING', 'RETRY_WAIT')
                  AND available_at <= ?
                ORDER BY created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE fraud_propagation_jobs j
            SET status = 'RUNNING',
                worker_token = ?,
                lease_until = ?,
                started_at = ?,
                attempt_count = j.attempt_count + 1,
                updated_at = ?
            FROM next_job
            WHERE j.id = next_job.id
            RETURNING j.id, j.entity_id, j.status, j.as_of, j.worker_token,
                      j.lease_until, j.attempt_count, j.available_at, j.started_at,
                      j.completed_at, j.last_error, j.model_version, j.created_at, j.updated_at
            """,
            rowMapper,
            Timestamp.from(now),
            workerToken,
            Timestamp.from(leaseUntil),
            Timestamp.from(now),
            Timestamp.from(now)
        );

        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.getFirst());
    }

    @Override
    public boolean renewLease(@NonNull UUID jobId, @NonNull UUID workerToken, @NonNull Duration extension) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(workerToken, "workerToken cannot be null");
        Objects.requireNonNull(extension, "extension cannot be null");

        Instant now = Instant.now();
        Instant newLeaseUntil = now.plus(extension);

        int rows = jdbc.update("""
            UPDATE fraud_propagation_jobs
            SET lease_until = ?,
                updated_at = ?
            WHERE id = ?
              AND worker_token = ?
              AND status = 'RUNNING'
            """,
            Timestamp.from(newLeaseUntil),
            Timestamp.from(now),
            jobId,
            workerToken
        );
        return rows > 0;
    }

    @Override
    public boolean complete(@NonNull UUID jobId, @NonNull UUID workerToken) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(workerToken, "workerToken cannot be null");

        Instant now = Instant.now();
        int rows = jdbc.update("""
            UPDATE fraud_propagation_jobs
            SET status = 'COMPLETED',
                completed_at = ?,
                updated_at = ?
            WHERE id = ?
              AND worker_token = ?
              AND status = 'RUNNING'
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            jobId,
            workerToken
        );
        return rows > 0;
    }

    @Override
    public boolean scheduleRetry(@NonNull UUID jobId, @NonNull UUID workerToken, @NonNull String lastError, @NonNull Duration retryDelay) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(workerToken, "workerToken cannot be null");
        Objects.requireNonNull(lastError, "lastError cannot be null");
        Objects.requireNonNull(retryDelay, "retryDelay cannot be null");

        Instant now = Instant.now();
        Instant nextAvailable = now.plus(retryDelay);

        int rows = jdbc.update("""
            UPDATE fraud_propagation_jobs
            SET status = 'RETRY_WAIT',
                available_at = ?,
                last_error = ?,
                worker_token = NULL,
                lease_until = NULL,
                updated_at = ?
            WHERE id = ?
              AND worker_token = ?
              AND status = 'RUNNING'
            """,
            Timestamp.from(nextAvailable),
            lastError,
            Timestamp.from(now),
            jobId,
            workerToken
        );
        return rows > 0;
    }

    @Override
    public boolean fail(@NonNull UUID jobId, @NonNull UUID workerToken, @NonNull String lastError) {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(workerToken, "workerToken cannot be null");
        Objects.requireNonNull(lastError, "lastError cannot be null");

        Instant now = Instant.now();
        int rows = jdbc.update("""
            UPDATE fraud_propagation_jobs
            SET status = 'FAILED',
                completed_at = ?,
                last_error = ?,
                updated_at = ?
            WHERE id = ?
              AND worker_token = ?
              AND status = 'RUNNING'
            """,
            Timestamp.from(now),
            lastError,
            Timestamp.from(now),
            jobId,
            workerToken
        );
        return rows > 0;
    }

    @Override
    public int reapExpiredLeases() {
        Instant now = Instant.now();
        return jdbc.update("""
            UPDATE fraud_propagation_jobs
            SET status = 'RETRY_WAIT',
                available_at = ?,
                last_error = 'Lease expired / worker unresponsive',
                worker_token = NULL,
                lease_until = NULL,
                updated_at = ?
            WHERE status = 'RUNNING'
              AND lease_until < ?
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    @Override
    public @NonNull Optional<PropagationJob> findById(@NonNull UUID jobId) {
        Objects.requireNonNull(jobId, "jobId cannot be null");

        List<PropagationJob> jobs = jdbc.query("""
            SELECT id, entity_id, status, as_of, worker_token, lease_until,
                   attempt_count, available_at, started_at, completed_at,
                   last_error, model_version, created_at, updated_at
            FROM fraud_propagation_jobs
            WHERE id = ?
            """,
            rowMapper,
            jobId
        );
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.getFirst());
    }

    private PropagationJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PropagationJob(
            rs.getObject("id", UUID.class),
            rs.getObject("entity_id", UUID.class),
            PropagationJobStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("as_of").toInstant(),
            rs.getObject("worker_token", UUID.class),
            rs.getTimestamp("lease_until") != null ? rs.getTimestamp("lease_until").toInstant() : null,
            rs.getInt("attempt_count"),
            rs.getTimestamp("available_at").toInstant(),
            rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
            rs.getString("last_error"),
            rs.getString("model_version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
