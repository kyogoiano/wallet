# 📐 Architecture Plan: PLAN-000.5 — Hybrid Fraud Intelligence & Relational Graph Projection

- **Associated Spec**: [`SPEC-000.5-hybrid-fraud-intelligence-and-relational-graph.md`](file:///.spec/SPEC-000.5-hybrid-fraud-intelligence-and-relational-graph.md)
- **Status**: Approved & Ready for Tasks Breakdown
- **Author**: Antigravity Financial & Risk Engineering Team
- **Date**: 2026-08-30
- **Source Reference**: [`.histories/history12.txt`](file:///.histories/history12.txt), [`.histories/history13.txt`](file:///.histories/history13.txt), [`.histories/history14.txt`](file:///.histories/history14.txt), [`.histories/history15.txt`](file:///.histories/history15.txt), [`.histories/history16.txt`](file:///.histories/history16.txt)

---

## 1. System Architecture & Modulith Boundaries

```mermaid
flowchart TD
    subgraph CoreDomain ["Core Ledger (br.com.wallet.ledger)"]
        TxUC[TransferFundsUseCase] --> OutboxDao[(Outbox Table)]
        OutboxDao --> Relay[Outbox Relay]
    end

    subgraph Messaging ["Durable Event Backbone"]
        Relay -->|NATS JetStream: events.wallet.*| NATS[NATS Broker]
    end

    subgraph FraudModule ["Fraud & Risk Engine (br.com.wallet.fraud)"]
        subgraph HotPath ["Synchronous O(1) Fraud Gate"]
            Gate[FraudRuleEngine] --> Caffeine[(Local Sliding Window)]
            Gate --> DF_Hot[(DragonflyDB: user:id:graph_risk)]
        end

        subgraph Intelligence ["Asynchronous Fraud Intelligence Layer"]
            NATS --> Listener[FraudGraphEventListener]
            Listener --> Projector[RelationalGraphProjector]
            
            Projector --> PG_Rel[(PostgreSQL: fraud_relationships)]
            Projector --> PG_Evt[(PostgreSQL: fraud_relationship_events)]
            Projector --> PG_Ent[(PostgreSQL: fraud_entities)]
            
            Projector --> PatternEngine[GraphPatternEngine]
            PatternEngine --> RiskCalc[GraphRiskCalculator]
            RiskCalc --> Materializer[DragonflyHotRiskMaterializer]
            Materializer --> DF_Hot
            
            Rebuild[GraphRebuildService] -.->|Rebuilds from events| PG_Rel
            Rebuild -.->|Reconstructs hot state| DF_Hot
        end
    end

    TxUC -->|Pre-execution O(1) check| Gate
```

---

## 2. PostgreSQL Schema & Data Storage Model

### 2.1. Migration Script (`docker/init/05-fraud-graph-schema.sql`)

```sql
-- 1. Entity Registry with Multi-Signal Risk Decomposition
CREATE TABLE IF NOT EXISTS fraud_entities (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL, -- 'USER', 'WALLET', 'DEVICE', 'IP', 'PHONE'
    direct_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    graph_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    behavioral_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    propagated_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    final_risk DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata JSONB
);

-- 2. Aggregate Relational Graph Edges (Current State Projection)
CREATE TABLE IF NOT EXISTS fraud_relationships (
    source_id UUID NOT NULL,
    target_id UUID NOT NULL,
    relationship_type VARCHAR(32) NOT NULL, -- 'TRANSFERRED_TO', 'OWNS', 'USES', 'LOGGED_FROM'
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    tx_count BIGINT NOT NULL DEFAULT 1,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    metadata JSONB,
    PRIMARY KEY (source_id, target_id, relationship_type)
);

CREATE INDEX IF NOT EXISTS idx_fraud_rel_source ON fraud_relationships (source_id, relationship_type);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_target ON fraud_relationships (target_id, relationship_type);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_last_seen ON fraud_relationships (last_seen_at);

-- 3. Temporal Relationship Evidence (Granular Fact Log for G_<=t and Cycle Sequence)
CREATE TABLE IF NOT EXISTS fraud_relationship_events (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL,
    target_id UUID NOT NULL,
    relationship_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    operation_id UUID,
    amount NUMERIC(19, 4),
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_fraud_rel_events_src_time ON fraud_relationship_events (source_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_events_tgt_time ON fraud_relationship_events (target_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_fraud_rel_events_type_time ON fraud_relationship_events (relationship_type, occurred_at);
```

---

## 3. Technology-Agnostic Interface Contracts

### 3.1. Domain Models (`br.com.wallet.fraud.intelligence.domain`)

```java
package br.com.wallet.fraud.intelligence.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FraudEntity(
    UUID id,
    EntityType entityType,
    double directRisk,
    double graphRisk,
    double behavioralRisk,
    double propagatedRisk,
    double finalRisk,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> metadata
) {}

public enum EntityType {
    USER, WALLET, DEVICE, IP, PHONE, CARD
}

public record FraudRelationship(
    UUID sourceId,
    UUID targetId,
    RelationshipType relationshipType,
    Instant firstSeenAt,
    Instant lastSeenAt,
    long txCount,
    java.math.BigDecimal totalAmount,
    Map<String, Object> metadata
) {}

public enum RelationshipType {
    TRANSFERRED_TO, OWNS, USES, LOGGED_FROM, SHARES
}

public record FraudRelationshipEvent(
    UUID id,
    UUID sourceId,
    UUID targetId,
    RelationshipType relationshipType,
    Instant occurredAt,
    UUID operationId,
    java.math.BigDecimal amount,
    Map<String, Object> metadata
) {}

public record GraphRiskSignals(
    double cycleRisk,
    double fanInRisk,
    double fanOutRisk,
    double sharedIdentityRisk,
    double muleHubRisk
) {
    public double calculateCompositeScore() {
        double weighted = (0.35 * cycleRisk) 
                        + (0.25 * muleHubRisk) 
                        + (0.20 * sharedIdentityRisk) 
                        + (0.10 * fanInRisk) 
                        + (0.10 * fanOutRisk);
        return Math.clamp(weighted, 0.0, 1.0);
    }
}
```

### 3.2. Storage & Query Contracts (`br.com.wallet.fraud.intelligence.domain`)

```java
public interface FraudRelationshipStore {
    void recordRelationshipEvent(FraudRelationshipEvent event);
    void upsertRelationship(FraudRelationship relationship);
    void upsertEntity(FraudEntity entity);
    Optional<FraudEntity> findEntity(UUID id);
    void updateGraphRisk(UUID entityId, double graphRisk, Instant updatedAt);
}

public interface FraudGraphQuery {
    List<UUID> detectCycles(UUID startNodeId, Duration window, int maxHops, Instant asOf);
    List<UUID> findSharedEntities(UUID entityId, EntityType targetType, int maxHops, Instant asOf);
    long countUniqueCounterparties(UUID sourceId, Duration window, Instant asOf);
}

public interface FraudFeatureProvider {
    GraphRiskSignals evaluateGraphSignals(UUID entityId, Instant asOf);
}
```

---

## 4. Asynchronous Pipeline & Hot Feature Feedback

### 4.1. `RelationalGraphProjector` (Idempotent Ingestion)
```java
@Service
public class RelationalGraphProjector {
    private final FraudRelationshipStore store;
    private final GraphPatternEngine patternEngine;
    private final DragonflyHotRiskMaterializer materializer;

    @Transactional
    public void projectTransfer(UUID fromWallet, UUID toWallet, UUID fromUser, UUID toUser, BigDecimal amount, UUID opId, Instant timestamp) {
        // 1. Immutable Event
        var event = new FraudRelationshipEvent(
            UUID.randomUUID(), fromWallet, toWallet, RelationshipType.TRANSFERRED_TO, timestamp, opId, amount, Map.of()
        );
        store.recordRelationshipEvent(event);

        // 2. Aggregate Edge Upsert
        store.upsertRelationship(new FraudRelationship(fromWallet, toWallet, RelationshipType.TRANSFERRED_TO, timestamp, timestamp, 1, amount, Map.of()));

        // 3. Evaluate Patterns & Signals
        var signals = patternEngine.evaluateGraphSignals(fromUser, timestamp);
        double graphScore = signals.calculateCompositeScore();

        // 4. Update PostgreSQL & Dragonfly Derived State
        store.updateGraphRisk(fromUser, graphScore, timestamp);
        materializer.materializeGraphRisk(fromUser, graphScore);
    }
}
```

### 4.2. `HotRiskMaterializer` (Derived Hot State)
```java
@Component
public class DragonflyHotRiskMaterializer {
    private final StringRedisTemplate redisTemplate;

    public void materializeGraphRisk(UUID userId, double graphRisk) {
        String key = "user:" + userId + ":graph_risk";
        redisTemplate.opsForValue().set(key, String.valueOf(graphRisk), Duration.ofHours(24));
    }
}
```

---

## 5. Failure Modes & Invariant Enforcement

| Invariant / Failure Mode | Strategy & Mitigation |
| :--- | :--- |
| **`I-FRAUD-001` (Core Transfer Isolation)** | Ingestion and graph evaluation execute completely asynchronously via NATS. NATS consumer errors retry with exponential backoff without affecting the core ledger. |
| **`I-FRAUD-002` ($O(1)$ Synchronous Gate)** | Fraud Gate reads `user:{id}:graph_risk` in $< 0.5\text{ms}$ via DragonflyDB Lettuce client without performing SQL/CTE queries on the hot path. |
| **`I-FRAUD-003` (Zero Future Leakage)** | `FraudGraphQuery` SQL CTE queries apply `occurred_at <= :asOf` on `fraud_relationship_events`. |
| **`I-FRAUD-005` (Idempotent Projection)** | `ON CONFLICT (source_id, target_id, relationship_type) DO UPDATE` increments `tx_count` and adds `total_amount`. |
| **`I-FRAUD-007` (Derived Hot State)** | `GraphRebuildService` can replay `fraud_relationship_events` to restore all DragonflyDB keys after cache eviction. |
