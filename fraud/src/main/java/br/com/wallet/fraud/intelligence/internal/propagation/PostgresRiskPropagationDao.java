package br.com.wallet.fraud.intelligence.internal.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.propagation.PropagatedEntityRisk;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL implementation of graph path traversal and risk persistence using recursive CTEs.
 */
@Repository
public class PostgresRiskPropagationDao {

    private final JdbcTemplate jdbc;

    public PostgresRiskPropagationDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public record DiscoveredPath(
        UUID sourceId,
        UUID targetId,
        int hopCount,
        List<UUID> nodes,
        List<RelationshipType> edgeTypes,
        List<Instant> edgeTimes
    ) {}

    /**
     * Discovers all paths originating from sourceEntityId using recursive CTE on historical events.
     */
    @NonNull
    public List<DiscoveredPath> findPathsFromSource(
        @NonNull UUID sourceEntityId,
        @NonNull Instant asOf,
        int maxHops,
        int maxPaths
    ) {
        Objects.requireNonNull(sourceEntityId, "sourceEntityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        Timestamp asOfTs = Timestamp.from(asOf);

        return jdbc.query("""
            WITH RECURSIVE propagation_paths AS (
                -- Anchor: 1-hop outgoing edges from source
                SELECT 
                    e.source_id,
                    e.target_id,
                    1 AS hop_count,
                    ARRAY[e.source_id, e.target_id]::uuid[] AS path_nodes,
                    ARRAY[e.relationship_type]::text[] AS edge_types,
                    ARRAY[e.occurred_at]::timestamptz[] AS edge_times
                FROM fraud_relationship_events e
                WHERE e.source_id = ?
                  AND e.occurred_at <= ?
                  AND e.source_id != e.target_id

                UNION ALL

                -- Recursive step: traverse outgoing edges from current target
                SELECT 
                    p.source_id,
                    e.target_id,
                    p.hop_count + 1,
                    p.path_nodes || e.target_id,
                    p.edge_types || e.relationship_type::text,
                    p.edge_times || e.occurred_at
                FROM propagation_paths p
                JOIN fraud_relationship_events e 
                  ON p.target_id = e.source_id
                 AND e.occurred_at <= ?
                WHERE p.hop_count < ?
                  AND NOT (e.target_id = ANY(p.path_nodes))
            )
            SELECT source_id, target_id, hop_count, path_nodes, edge_types, edge_times
            FROM propagation_paths
            LIMIT ?
            """,
            this::mapDiscoveredPath,
            sourceEntityId,
            asOfTs,
            asOfTs,
            maxHops,
            maxPaths
        );
    }

    /**
     * Discovers paths connecting a specific source and target.
     */
    @NonNull
    public List<DiscoveredPath> findPathsBetween(
        @NonNull UUID sourceEntityId,
        @NonNull UUID targetEntityId,
        @NonNull Instant asOf,
        int maxHops,
        int maxPaths
    ) {
        Objects.requireNonNull(sourceEntityId, "sourceEntityId cannot be null");
        Objects.requireNonNull(targetEntityId, "targetEntityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        Timestamp asOfTs = Timestamp.from(asOf);

        return jdbc.query("""
            WITH RECURSIVE propagation_paths AS (
                SELECT 
                    e.source_id,
                    e.target_id,
                    1 AS hop_count,
                    ARRAY[e.source_id, e.target_id]::uuid[] AS path_nodes,
                    ARRAY[e.relationship_type]::text[] AS edge_types,
                    ARRAY[e.occurred_at]::timestamptz[] AS edge_times
                FROM fraud_relationship_events e
                WHERE e.source_id = ?
                  AND e.occurred_at <= ?
                  AND e.source_id != e.target_id

                UNION ALL

                SELECT 
                    p.source_id,
                    e.target_id,
                    p.hop_count + 1,
                    p.path_nodes || e.target_id,
                    p.edge_types || e.relationship_type::text,
                    p.edge_times || e.occurred_at
                FROM propagation_paths p
                JOIN fraud_relationship_events e 
                  ON p.target_id = e.source_id
                 AND e.occurred_at <= ?
                WHERE p.hop_count < ?
                  AND NOT (e.target_id = ANY(p.path_nodes))
            )
            SELECT source_id, target_id, hop_count, path_nodes, edge_types, edge_times
            FROM propagation_paths
            WHERE target_id = ?
            LIMIT ?
            """,
            this::mapDiscoveredPath,
            sourceEntityId,
            asOfTs,
            asOfTs,
            maxHops,
            targetEntityId,
            maxPaths
        );
    }

    /**
     * Gets the direct risk score of an entity.
     */
    public double getDirectRisk(@NonNull UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        List<Double> results = jdbc.query(
            "SELECT direct_risk FROM fraud_entities WHERE id = ?",
            (rs, rowNum) -> rs.getDouble("direct_risk"),
            entityId
        );
        return results.isEmpty() ? 0.0 : results.getFirst();
    }

    /**
     * Batch persists calculated propagated risk scores without modifying direct_risk or final_risk (I-PROP-005).
     */
    public void persistPropagatedRisks(@NonNull Map<UUID, PropagatedEntityRisk> risks) {
        Objects.requireNonNull(risks, "risks cannot be null");
        if (risks.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = new ArrayList<>(risks.size());
        for (PropagatedEntityRisk risk : risks.values()) {
            batchArgs.add(new Object[]{
                risk.propagatedRisk(),
                risk.modelVersion(),
                Timestamp.from(risk.evaluatedAt()),
                risk.entityId()
            });
        }

        jdbc.batchUpdate("""
            UPDATE fraud_entities
            SET propagated_risk = ?,
                propagation_model_version = ?,
                propagation_evaluated_at = ?,
                updated_at = NOW()
            WHERE id = ?
            """,
            batchArgs
        );
    }

    private DiscoveredPath mapDiscoveredPath(ResultSet rs, int rowNum) throws SQLException {
        UUID sourceId = rs.getObject("source_id", UUID.class);
        UUID targetId = rs.getObject("target_id", UUID.class);
        int hopCount = rs.getInt("hop_count");

        Array nodesArray = rs.getArray("path_nodes");
        List<UUID> nodes = nodesArray != null ? Arrays.asList((UUID[]) nodesArray.getArray()) : Collections.emptyList();

        Array edgeTypesArray = rs.getArray("edge_types");
        List<RelationshipType> edgeTypes = new ArrayList<>();
        if (edgeTypesArray != null) {
            String[] rawTypes = (String[]) edgeTypesArray.getArray();
            for (String rt : rawTypes) {
                try {
                    edgeTypes.add(RelationshipType.valueOf(rt));
                } catch (IllegalArgumentException e) {
                    edgeTypes.add(RelationshipType.SHARES);
                }
            }
        }

        Array edgeTimesArray = rs.getArray("edge_times");
        List<Instant> edgeTimes = new ArrayList<>();
        if (edgeTimesArray != null) {
            Timestamp[] rawTimestamps = (Timestamp[]) edgeTimesArray.getArray();
            for (Timestamp ts : rawTimestamps) {
                edgeTimes.add(ts.toInstant());
            }
        }

        return new DiscoveredPath(sourceId, targetId, hopCount, nodes, edgeTypes, edgeTimes);
    }
}
