package br.com.wallet.ledger.internal.persistence;

import br.com.wallet.ledger.api.event.DomainEvent;
import br.com.wallet.ledger.api.event.DomainEventType;
import br.com.wallet.ledger.internal.outbox.OutboxEvent;
import br.com.wallet.ledger.api.utils.JsonUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxDao <T extends DomainEvent> {

    private final JdbcTemplate jdbc;
    private final JsonUtils jsonUtils;

    public OutboxDao(final JdbcTemplate jdbc, JsonUtils jsonUtils) {
        this.jdbc = jdbc;
        this.jsonUtils = jsonUtils;
    }

    public void save(@NonNull final T event) {
        jdbc.update("""
            INSERT INTO outbox (
                id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload,
                partition_key
            )
            VALUES (?, ?, ?, ?, ?::jsonb, ?)
        """,
                UUID.randomUUID(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType().name(),
                jsonUtils.toJson(event),
                event.partitionKey()
        );
    }

    @NonNull
    public List<OutboxEvent> getOutboxEvents(@NonNull Instant now) {
        return jdbc.query("""
                    SELECT id, event_type, payload, retry_count, aggregate_id, aggregate_type, partition_key
                    FROM outbox
                    WHERE status IN ('PENDING', 'FAILED')
                        AND (next_retry_at IS NULL OR next_retry_at <= ?)
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 10
                """, (rs, rowNum) -> new OutboxEvent(
                rs.getObject("id", UUID.class),
                DomainEventType.valueOf(rs.getString("event_type")),
                rs.getString("payload"),
                rs.getObject("retry_count", Integer.class),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getObject("partition_key", UUID.class)
        ), now.atOffset(ZoneOffset.UTC));
    }


    public List<OutboxEvent> claimBatch(@NonNull Instant now, @NonNull Integer limit) {
        return jdbc.query("""
           WITH claimed AS (
              SELECT id
              FROM outbox
              WHERE status IN ('PENDING', 'FAILED')
                AND (next_retry_at IS NULL OR next_retry_at <= ?)
              ORDER BY created_at
              LIMIT ?
              FOR UPDATE SKIP LOCKED
          )
          UPDATE outbox o
          SET status = 'PROCESSING'
          FROM claimed
          WHERE o.id = claimed.id
          RETURNING o.id, o.event_type, o.payload, o.retry_count, o.aggregate_id, o.aggregate_type, o.partition_key
        """,
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("id", UUID.class),
                        DomainEventType.valueOf(rs.getString("event_type")),
                        rs.getString("payload"),
                        rs.getObject("retry_count", Integer.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getObject("partition_key", UUID.class)
                ),
                now.atOffset(ZoneOffset.UTC),
                limit
        );
    }


    /**
     * Mark event as failed, increase number of retries and schedule next retry using exponential backoff
     *
     * @param id  outbox id
     * @param now current instant
     */
    public void markFailed(@NonNull final UUID id, @NonNull final Instant now) {
        jdbc.update("""
            UPDATE outbox
            SET status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_at = ? + (INTERVAL '1 second' * POWER(2, retry_count +1))
            WHERE id = ?
        """, now.atOffset(ZoneOffset.UTC), id);
    }

    /**
     * Mark event as processed
     */
    public void markAsProcessed(@NonNull final UUID id, @NonNull final Instant now) {
        jdbc.update("""
            UPDATE outbox
            SET status = 'PROCESSED', processed_at = ?
            WHERE id = ?
        """, now.atOffset(ZoneOffset.UTC), id);
    }

    /**
     * Mark event as dead
     */
    public void markAsDead(@NonNull final UUID id, @NonNull final Instant now) {
        jdbc.update("""
            UPDATE outbox
            SET status = 'DEAD', processed_at = ?
            WHERE id = ?
        """, now.atOffset(ZoneOffset.UTC), id);
    }
}
