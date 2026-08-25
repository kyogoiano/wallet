package br.com.wallet.infrastructure.persistence;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.infrastructure.messaging.dlq.DlqEvent;
import br.com.wallet.infrastructure.messaging.dlq.DlqFailureType;
import br.com.wallet.infrastructure.messaging.dlq.DlqStatus;
import br.com.wallet.ledger.api.utils.JsonUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Repository
public class DlqOperationsDao {

    private final JdbcTemplate jdbc;
    private final JsonUtils jsonUtils;

    public DlqOperationsDao(final JdbcTemplate jdbc, JsonUtils jsonUtils) {
        this.jdbc = jdbc;
        this.jsonUtils = jsonUtils;
    }

    /**
     * Claim Batch with fairness on next retry at and created at
     * @param now current instant
     * @param limit batch size
     * @return list of dlq events
     */
    public List<DlqEvent> claimBatch(@NonNull Instant now, @NonNull Integer limit) {
        return jdbc.query("""
            WITH claimed AS (
                      SELECT id
                      FROM dlq_operations
                      WHERE status IN ('PENDING', 'FAILED')
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
                (rs, rowNum) -> new DlqEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("operation_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("subject"),
                        DlqStatus.valueOf(rs.getString("status")),
                        rs.getString("error"),
                        rs.getString("payload"),
                        rs.getObject("retry_count", Integer.class),
                        Objects.isNull(rs.getTimestamp("next_retry_at")) ? null : rs.getTimestamp("next_retry_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        Objects.isNull(rs.getTimestamp("processed_at")) ? null : rs.getTimestamp("processed_at").toInstant(),
                        DlqFailureType.valueOf(rs.getString("failure_type")),
                        rs.getString("event_type")
                ),
                now.atOffset(ZoneOffset.UTC),
                limit
        );
    }

    public void markFailed(@NonNull final UUID id, @NonNull final Instant now, DlqFailureType failureType) {
        jdbc.update("""
            update dlq_operations
            set retry_count = retry_count + 1,
                status = 'FAILED',
                failure_type = ?,
                next_retry_at = ? + (INTERVAL '1 second' * POWER(2, retry_count +1))
            WHERE id = ?
        """, failureType.name(), now.atOffset(ZoneOffset.UTC), id);
    }

    public void markAsCompleted(@NonNull final UUID id, @NonNull final Instant now) {
        jdbc.update("""
            UPDATE dlq_operations
            SET status = 'COMPLETED', processed_at = ?
            WHERE id = ?
            """, now.atOffset(ZoneOffset.UTC), id);
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
        // Example:
        // FOR VALUES FROM ('2026-04-01') TO ('2026-04-02')

        final var matcher = Pattern.compile("TO \\('([^']+)'\\)").matcher(bounds);

        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse partition bound: " + bounds);
        }

        final String upper = matcher.group(1);

        return Instant.parse(upper);
    }

    /**
     * As we don't rely on pgPartman, we should create partition tables driven by a scheduler
     * @param now current instant ( one partition per week )
     */
    public void createPartition(@NonNull final Instant now) {

        final var fromDate = now.atZone(ZoneOffset.UTC).toLocalDate().toString().replace("-", "_");

        final String partitionName = "dlq_operations_" + fromDate;

        final var cutOff = now.atZone(ZoneOffset.UTC)
                .plusDays(7).toLocalDate();

        final var toDate = cutOff.toString().replace("-", "_");

        jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS %s
                    PARTITION OF dlq_operations
                    FOR VALUES FROM (%s) TO (%s);
                """.formatted(partitionName, fromDate, toDate));
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
}
