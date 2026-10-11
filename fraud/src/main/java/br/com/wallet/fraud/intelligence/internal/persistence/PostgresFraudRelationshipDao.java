package br.com.wallet.fraud.intelligence.internal.persistence;

import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudGraphQuery;
import br.com.wallet.fraud.intelligence.domain.FraudRelationship;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipEvent;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresFraudRelationshipDao implements FraudRelationshipStore, FraudGraphQuery {

    private final JdbcTemplate jdbc;

    public PostgresFraudRelationshipDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    @Override
    public void upsertEntity(@NonNull final FraudEntity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        jdbc.update("""
            INSERT INTO fraud_entities (
                id, entity_type, direct_risk, graph_risk, behavioral_risk,
                propagated_risk, final_risk, created_at, updated_at, metadata
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO UPDATE SET
                entity_type = EXCLUDED.entity_type,
                direct_risk = EXCLUDED.direct_risk,
                graph_risk = EXCLUDED.graph_risk,
                behavioral_risk = EXCLUDED.behavioral_risk,
                propagated_risk = EXCLUDED.propagated_risk,
                final_risk = EXCLUDED.final_risk,
                updated_at = EXCLUDED.updated_at,
                metadata = EXCLUDED.metadata;
        """,
            entity.id(),
            entity.entityType().name(),
            entity.directRisk(),
            entity.graphRisk(),
            entity.behavioralRisk(),
            entity.propagatedRisk(),
            entity.finalRisk(),
            Timestamp.from(entity.createdAt()),
            Timestamp.from(entity.updatedAt()),
            "{}"
        );
    }

    @Override
    @NonNull
    public Optional<FraudEntity> findEntityById(@NonNull final UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        List<FraudEntity> list = jdbc.query("""
            SELECT id, entity_type, direct_risk, graph_risk, behavioral_risk,
                   propagated_risk, final_risk, created_at, updated_at
            FROM fraud_entities
            WHERE id = ?
        """, (rs, rowNum) -> mapEntity(rs), id);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public void updateGraphRisk(@NonNull final UUID entityId, final double graphRisk, @NonNull final Instant updatedAt) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        jdbc.update("""
            UPDATE fraud_entities
            SET graph_risk = ?, updated_at = ?
            WHERE id = ?
        """, graphRisk, Timestamp.from(updatedAt), entityId);
    }

    @Override
    public void upsertRelationship(@NonNull final FraudRelationship relationship) {
        Objects.requireNonNull(relationship, "relationship cannot be null");
        jdbc.update("""
            INSERT INTO fraud_relationships (
                source_id, target_id, relationship_type, first_seen_at, last_seen_at, tx_count, total_amount, metadata
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (source_id, target_id, relationship_type) DO UPDATE SET
                last_seen_at = EXCLUDED.last_seen_at,
                tx_count = fraud_relationships.tx_count + EXCLUDED.tx_count,
                total_amount = fraud_relationships.total_amount + EXCLUDED.total_amount;
        """,
            relationship.sourceId(),
            relationship.targetId(),
            relationship.relationshipType().name(),
            Timestamp.from(relationship.firstSeenAt()),
            Timestamp.from(relationship.lastSeenAt()),
            relationship.txCount(),
            relationship.totalAmount(),
            "{}"
        );
    }

    @Override
    @NonNull
    public Optional<FraudRelationship> findRelationship(
        @NonNull final UUID sourceId,
        @NonNull final UUID targetId,
        @NonNull final RelationshipType type
    ) {
        Objects.requireNonNull(sourceId, "sourceId cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        List<FraudRelationship> list = jdbc.query("""
            SELECT source_id, target_id, relationship_type, first_seen_at, last_seen_at, tx_count, total_amount
            FROM fraud_relationships
            WHERE source_id = ? AND target_id = ? AND relationship_type = ?
        """, (rs, rowNum) -> mapRelationship(rs), sourceId, targetId, type.name());

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public void recordRelationshipEvent(@NonNull final FraudRelationshipEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        jdbc.update("""
            INSERT INTO fraud_relationship_events (
                id, source_id, target_id, relationship_type, occurred_at, operation_id, amount, metadata
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
        """,
            event.id(),
            event.sourceId(),
            event.targetId(),
            event.relationshipType().name(),
            Timestamp.from(event.occurredAt()),
            event.operationId(),
            event.amount() != null ? event.amount() : BigDecimal.ZERO,
            "{}"
        );
    }

    @Override
    @NonNull
    public List<UUID> detectCycles(
        @NonNull final UUID startNodeId,
        @NonNull final Duration window,
        final int maxHops,
        @NonNull final Instant asOf
    ) {
        Objects.requireNonNull(startNodeId, "startNodeId cannot be null");
        Objects.requireNonNull(window, "window cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        Instant windowStart = asOf.minus(window);
        int effectiveHops = Math.clamp(maxHops, 2, 5);

        // Recursive CTE checking cycle back to startNodeId within [windowStart, asOf]
        String sql = """
            WITH RECURSIVE cycle_search(current_node, target_node, path, hop_count, last_occurred) AS (
                -- Base case: 1-hop outgoing edges from startNodeId
                SELECT
                    target_id,
                    target_id,
                    ARRAY[source_id, target_id]::UUID[],
                    1,
                    occurred_at
                FROM fraud_relationship_events
                WHERE source_id = ?
                  AND relationship_type = 'TRANSFERRED_TO'
                  AND occurred_at >= ?
                  AND occurred_at <= ?
               \s
                UNION ALL
               \s
                -- Recursive step: follow outgoing edges
                SELECT
                    e.target_id,
                    cs.target_node,
                    cs.path || e.target_id,
                    cs.hop_count + 1,
                    e.occurred_at
                FROM cycle_search cs
                JOIN fraud_relationship_events e ON e.source_id = cs.current_node
                WHERE e.relationship_type = 'TRANSFERRED_TO'
                  AND e.occurred_at >= cs.last_occurred
                  AND e.occurred_at <= ?
                  AND cs.hop_count < ?
                  AND (e.target_id = ? OR NOT (e.target_id = ANY(cs.path)))
            )
            SELECT path
            FROM cycle_search
            WHERE current_node = ? AND hop_count >= 2
            LIMIT 1;
       \s""";

        List<List<UUID>> results = jdbc.query(
            sql,
            (rs, rowNum) -> {
                UUID[] array = (UUID[]) rs.getArray("path").getArray();
                return List.of(array);
            },
            startNodeId,
            Timestamp.from(windowStart),
            Timestamp.from(asOf),
            Timestamp.from(asOf),
            effectiveHops,
            startNodeId,
            startNodeId
        );

        return results.isEmpty() ? Collections.emptyList() : results.getFirst();
    }

    @Override
    @NonNull
    public List<UUID> findSharedEntities(
        @NonNull final UUID entityId,
        @NonNull final EntityType targetType,
        final int maxHops,
        @NonNull final Instant asOf
    ) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(targetType, "targetType cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        // Find entities sharing devices, IPs, or phones with entityId
        String sql = """
            SELECT DISTINCT r2.source_id
            FROM fraud_relationships r1
            JOIN fraud_relationships r2 ON r1.target_id = r2.target_id
            WHERE r1.source_id = ?
              AND r2.source_id != ?
              AND r1.last_seen_at <= ?
              AND r2.last_seen_at <= ?;
        """;

        return jdbc.query(
            sql,
            (rs, rowNum) -> rs.getObject("source_id", UUID.class),
            entityId,
            entityId,
            Timestamp.from(asOf),
            Timestamp.from(asOf)
        );
    }

    @Override
    public long countUniqueCounterparties(
        @NonNull final UUID sourceId,
        @NonNull final Duration window,
        @NonNull final Instant asOf
    ) {
        Objects.requireNonNull(sourceId, "sourceId cannot be null");
        Objects.requireNonNull(window, "window cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        Instant windowStart = asOf.minus(window);

        Long count = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT target_id)
            FROM fraud_relationship_events
            WHERE source_id = ?
              AND relationship_type = 'TRANSFERRED_TO'
              AND occurred_at >= ?
              AND occurred_at <= ?;
        """, Long.class, sourceId, Timestamp.from(windowStart), Timestamp.from(asOf));

        return count != null ? count : 0L;
    }

    private static FraudEntity mapEntity(ResultSet rs) throws SQLException {
        return new FraudEntity(
            rs.getObject("id", UUID.class),
            EntityType.valueOf(rs.getString("entity_type")),
            rs.getDouble("direct_risk"),
            rs.getDouble("graph_risk"),
            rs.getDouble("behavioral_risk"),
            rs.getDouble("propagated_risk"),
            rs.getDouble("final_risk"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            Collections.emptyMap()
        );
    }

    private static FraudRelationship mapRelationship(ResultSet rs) throws SQLException {
        return new FraudRelationship(
            rs.getObject("source_id", UUID.class),
            rs.getObject("target_id", UUID.class),
            RelationshipType.valueOf(rs.getString("relationship_type")),
            rs.getTimestamp("first_seen_at").toInstant(),
            rs.getTimestamp("last_seen_at").toInstant(),
            rs.getLong("tx_count"),
            rs.getBigDecimal("total_amount"),
            Collections.emptyMap()
        );
    }
}
