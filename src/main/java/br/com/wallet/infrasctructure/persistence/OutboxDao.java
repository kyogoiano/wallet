package br.com.wallet.infrasctructure.persistence;

import br.com.wallet.domain.event.DomainEvent;
import br.com.wallet.infrasctructure.outbox.OutboxEvent;
import br.com.wallet.infrasctructure.utils.JsonUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxDao {

    private final JdbcTemplate jdbc;

    public OutboxDao(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(@NonNull final DomainEvent event) {
        jdbc.update("""
            INSERT INTO outbox (
                id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload
            )
            VALUES (?, ?, ?, ?, ?::jsonb)
        """,
                UUID.randomUUID(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                JsonUtils.toJson(event)
        );
    }

    @NonNull
    public List<OutboxEvent> getOutboxEvents(@NonNull Instant now) {
        return jdbc.query("""
                    SELECT id, event_type, payload
                    FROM outbox
                    WHERE status IN ('PENDING', 'FAILED')
                        AND (next_retry_at IS NULL OR next_retry_at <= ?)
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 10
                """, (rs, rowNum) -> new OutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                rs.getString("payload")
        ), now);
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
        """, now, id);
    }

    /**
     * Mark event as processed
     */
    public void markAsProcessed(@NonNull final UUID id, @NonNull final Instant now) {
        jdbc.update("""
            UPDATE outbox
            SET status = 'PROCESSED', processed_at = ?
            WHERE id = ?
        """, now, id);
    }
}
