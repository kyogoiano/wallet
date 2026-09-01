package br.com.wallet.fraud.intelligence.internal.service;

import br.com.wallet.fraud.intelligence.domain.FraudFeatureProvider;
import br.com.wallet.fraud.intelligence.domain.FraudRelationship;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipEvent;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import br.com.wallet.fraud.intelligence.domain.GraphRiskSignals;
import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.internal.materializer.HotRiskMaterializer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class GraphRebuildService {

    private static final Logger log = LoggerFactory.getLogger(GraphRebuildService.class);

    private final JdbcTemplate jdbc;
    private final FraudRelationshipStore store;
    private final FraudFeatureProvider featureProvider;
    private final HotRiskMaterializer materializer;

    public GraphRebuildService(
        @NonNull final JdbcTemplate jdbc,
        @NonNull final FraudRelationshipStore store,
        @NonNull final FraudFeatureProvider featureProvider,
        @NonNull final HotRiskMaterializer materializer
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.featureProvider = Objects.requireNonNull(featureProvider, "featureProvider cannot be null");
        this.materializer = Objects.requireNonNull(materializer, "materializer cannot be null");
    }

    public record RebuildSummary(
        long totalEventsProcessed,
        long relationshipsReconstructed,
        long entitiesRecomputed
    ) {}

    @Transactional
    public RebuildSummary rebuildAll(@NonNull final Instant asOf) {
        Objects.requireNonNull(asOf, "asOf cannot be null");
        log.info("Starting historical fraud graph rebuild as of {}", asOf);

        // 1. Reset aggregate relationships table
        jdbc.update("DELETE FROM fraud_relationships");

        // 2. Fetch all events up to asOf
        List<FraudRelationshipEvent> events = jdbc.query("""
            SELECT id, source_id, target_id, relationship_type, occurred_at, operation_id, amount
            FROM fraud_relationship_events
            WHERE occurred_at <= ?
            ORDER BY occurred_at ASC
        """, (rs, rowNum) -> new FraudRelationshipEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("source_id", UUID.class),
            rs.getObject("target_id", UUID.class),
            RelationshipType.valueOf(rs.getString("relationship_type")),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getObject("operation_id", UUID.class),
            rs.getBigDecimal("amount"),
            null
        ), java.sql.Timestamp.from(asOf));

        Set<UUID> touchedEntities = new HashSet<>();

        // 3. Replay and aggregate
        events.forEach(event -> {
            touchedEntities.add(event.sourceId());
            touchedEntities.add(event.targetId());
            BigDecimal amount = event.amount() != null ? event.amount() : BigDecimal.ZERO;
            FraudRelationship relationship = new FraudRelationship(
                    event.sourceId(),
                    event.targetId(),
                    event.relationshipType(),
                    event.occurredAt(),
                    event.occurredAt(),
                    1,
                    amount,
                    null
            );
            store.upsertRelationship(relationship);
        });

        // 4. Recalculate graph risk and rematerialize to DragonflyDB
        touchedEntities.forEach(entityId -> {
            GraphRiskSignals signals = featureProvider.evaluateGraphSignals(entityId, asOf);
            double score = signals.calculateCompositeScore();
            store.updateGraphRisk(entityId, score, asOf);
            materializer.materializeGraphRisk(entityId, score);
        });

        log.info("Graph rebuild completed: {} events, {} entities recomputed", events.size(), touchedEntities.size());
        return new RebuildSummary(events.size(), events.size(), touchedEntities.size());
    }
}
