package br.com.wallet.dlq.internal.persistence;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.ledger.api.utils.JsonUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Repository
public class DlqOperationsDao {

    public static final int MAX_AUTOMATIC_RETRIES = 3;

    private final JdbcTemplate jdbc;
    private final JsonUtils jsonUtils;

    private final RowMapper<DlqEvent> rowMapper = (rs, rowNum) -> new DlqEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("operation_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("subject"),
            DlqStatus.valueOf(rs.getString("status")),
            rs.getString("error"),
            rs.getString("payload"),
            rs.getObject("retry_count", Integer.class),
            rs.getTimestamp("next_retry_at") != null ? rs.getTimestamp("next_retry_at").toInstant() : null,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("processed_at") != null ? rs.getTimestamp("processed_at").toInstant() : null,
            DlqFailureType.valueOf(rs.getString("failure_type")),
            rs.getString("event_type")
    );

    public DlqOperationsDao(final JdbcTemplate jdbc, final JsonUtils jsonUtils) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
        this.jsonUtils = Objects.requireNonNull(jsonUtils, "jsonUtils cannot be null");
    }

    /**
     * Claim Batch with fairness on next retry at and created at.
     * Bounded by retry_count < MAX_AUTOMATIC_RETRIES (3 attempts).
     */
    @Traceable("dlq.claim_batch")
    public List<DlqEvent> claimBatch(@NonNull final Instant now, @NonNull final Integer limit) {
        return jdbc.query("""
            WITH claimed AS (
                      SELECT id
                      FROM dlq_operations
                      WHERE status IN ('PENDING', 'FAILED')
                        AND retry_count < ?
                        AND (next_retry_at IS NULL OR next_retry_at <= ?)
                      ORDER BY next_retry_at, created_at
                      LIMIT ?
                      FOR UPDATE SKIP LOCKED
                    )
                    UPDATE dlq_operations d
                    SET status = 'PROCESSING'
                    FROM claimed
                    WHERE d.id = claimed.id
                    RETURNING d.id, d.operation_id, d.user_id, d.subject, d.status, d.error, d.payload, d.retry_count, d.next_retry_at, d.created_at, d.processed_at, d.failure_type, d.event_type
        """,
                rowMapper,
                MAX_AUTOMATIC_RETRIES,
                now.atOffset(ZoneOffset.UTC),
                limit
        );
    }

    /**
     * Increment retry count. If retry_count >= 3, transition to EXHAUSTED and clear next_retry_at.
     */
    @Traceable("dlq.mark_failed")
    public void markFailed(@NonNull final UUID id, @NonNull final Instant now, @NonNull final DlqFailureType failureType) {
        jdbc.update("""
            UPDATE dlq_operations
            SET retry_count = retry_count + 1,
                status = CASE WHEN retry_count + 1 >= ? THEN 'EXHAUSTED' ELSE 'FAILED' END,
                failure_type = ?,
                next_retry_at = CASE WHEN retry_count + 1 >= ? THEN NULL ELSE ? + (INTERVAL '1 second' * POWER(2, retry_count + 1)) END
            WHERE id = ?
        """,
                MAX_AUTOMATIC_RETRIES,
                failureType.name(),
                MAX_AUTOMATIC_RETRIES,
                now.atOffset(ZoneOffset.UTC),
                id
        );
    }

    @Traceable("dlq.mark_completed")
    public void markAsCompleted(@NonNull final UUID id, @NonNull final Instant now) {
        jdbc.update("""
            UPDATE dlq_operations
            SET status = 'COMPLETED', processed_at = ?
            WHERE id = ?
            """, now.atOffset(ZoneOffset.UTC), id);
    }

    @Traceable("dlq.mark_discarded")
    public void markAsDiscarded(@NonNull final UUID id, @NonNull final Instant now, @Nullable final String reason) {
        jdbc.update("""
            UPDATE dlq_operations
            SET status = 'DISCARDED',
                error = COALESCE(?, error),
                processed_at = ?
            WHERE id = ?
            """, reason, now.atOffset(ZoneOffset.UTC), id);
    }

    @Traceable("dlq.mark_pending_replay")
    public void markAsPendingForReplay(@NonNull final UUID id, @NonNull final Instant now) {
        jdbc.update("""
            UPDATE dlq_operations
            SET status = 'PENDING',
                next_retry_at = ?,
                retry_count = 0
            WHERE id = ?
            """, now.atOffset(ZoneOffset.UTC), id);
    }

    public Optional<DlqEvent> findById(@NonNull final UUID id) {
        final List<DlqEvent> results = jdbc.query("""
            SELECT id, operation_id, user_id, subject, status, error, payload, retry_count, next_retry_at, created_at, processed_at, failure_type, event_type
            FROM dlq_operations
            WHERE id = ?
            LIMIT 1
        """, rowMapper, id);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<DlqEvent> findByFilter(@NonNull final DlqQueryFilter filter, final int limit, final int offset) {
        final StringBuilder sql = new StringBuilder("""
            SELECT id, operation_id, user_id, subject, status, error, payload, retry_count, next_retry_at, created_at, processed_at, failure_type, event_type
            FROM dlq_operations
            WHERE 1=1
        """);
        final List<Object> params = new ArrayList<>();

        if (filter.status() != null) {
            sql.append(" AND status = ?");
            params.add(filter.status().name());
        }
        if (filter.failureType() != null) {
            sql.append(" AND failure_type = ?");
            params.add(filter.failureType().name());
        }
        if (filter.eventType() != null && !filter.eventType().isBlank()) {
            sql.append(" AND event_type = ?");
            params.add(filter.eventType());
        }
        if (filter.operationId() != null) {
            sql.append(" AND operation_id = ?");
            params.add(filter.operationId());
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), rowMapper, params.toArray());
    }

    public List<DlqEvent> findExhaustedOperations(@NonNull final Integer limit) {
        return jdbc.query("""
            SELECT id, operation_id, user_id, subject, status, error, payload, retry_count, next_retry_at, created_at, processed_at, failure_type, event_type
            FROM dlq_operations
            WHERE status = 'EXHAUSTED'
            ORDER BY created_at ASC
            LIMIT ?
        """, rowMapper, limit);
    }

    @Traceable("dlq.insert")
    public void insert(@NonNull final DlqEvent dlqEvent) {
        jdbc.update("""
                    INSERT INTO dlq_operations (
                        id, operation_id, user_id, subject, status, error, payload,
                        retry_count, next_retry_at, created_at, processed_at, failure_type, event_type
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """,
                dlqEvent.id(),
                dlqEvent.operationId(),
                dlqEvent.userId(),
                dlqEvent.subject(),
                dlqEvent.status().name(),
                dlqEvent.error(),
                jsonUtils.toJson(dlqEvent.payload()),
                dlqEvent.retryCount(),
                dlqEvent.nextRetryAt() != null ? dlqEvent.nextRetryAt().atOffset(ZoneOffset.UTC) : null,
                dlqEvent.createdAt().atOffset(ZoneOffset.UTC),
                dlqEvent.processedAt() != null ? dlqEvent.processedAt().atOffset(ZoneOffset.UTC) : null,
                dlqEvent.failureType().name(),
                dlqEvent.eventType()
        );
    }

    public @NonNull Integer cleanUpWeekly(@NonNull final Instant now) {
        final var cutoff = now.minus(7, ChronoUnit.DAYS);
        final var partitionsToDrop = findPartitionsOlderThan(cutoff);

        for (final String partition : partitionsToDrop) {
            jdbc.execute("ALTER TABLE dlq_operations DETACH PARTITION " + partition + " CONCURRENTLY");
            jdbc.execute("DROP TABLE " + partition);
        }

        return partitionsToDrop.size();
    }

    private @NonNull List<String> findPartitionsOlderThan(@NonNull final Instant cutoff) {
        return jdbc.query("""
            SELECT
                child.relname AS partition_name,
                pg_get_expr(child.relpartbound, child.oid) AS bounds
            FROM pg_inherits
            JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
            JOIN pg_class child ON pg_inherits.inhrelid = child.oid
            WHERE parent.relname = 'dlq_operations'
            """,
                (rs, rowNum) -> {
                    final String name = rs.getString("partition_name");
                    final String bounds = rs.getString("bounds");
                    final Instant upperBound = extractUpperBound(bounds);
                    return upperBound.isBefore(cutoff) ? name : null;
                }
        ).stream()
        .filter(Objects::nonNull)
        .toList();
    }

    private Instant extractUpperBound(@NonNull final String bounds) {
        final var matcher = Pattern.compile("TO \\('([^']+)'\\)").matcher(bounds);
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse partition bound: " + bounds);
        }
        final String upper = matcher.group(1);
        return Instant.parse(upper);
    }

    public void createPartition(@NonNull final Instant now) {
        final var fromDate = now.atZone(ZoneOffset.UTC).toLocalDate().toString().replace("-", "_");
        final String partitionName = "dlq_operations_" + fromDate;
        final var cutOff = now.atZone(ZoneOffset.UTC).plusDays(7).toLocalDate();
        final var toDate = cutOff.toString().replace("-", "_");

        jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS %s
                    PARTITION OF dlq_operations
                    FOR VALUES FROM (%s) TO (%s);
                """.formatted(partitionName, fromDate, toDate));
    }
}
