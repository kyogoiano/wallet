# 📝 Task Breakdown: TASKS-000.5 — Hybrid Fraud Intelligence & Relational Graph Projection

- **Associated Spec**: [`SPEC-000.5-hybrid-fraud-intelligence-and-relational-graph.md`](file:///.spec/SPEC-000.5-hybrid-fraud-intelligence-and-relational-graph.md)
- **Associated Plan**: [`PLAN-000.5-hybrid-fraud-intelligence-and-relational-graph.md`](file:///.spec/PLAN-000.5-hybrid-fraud-intelligence-and-relational-graph.md)
- **Status**: Ready for Human Review & TDD Execution
- **Architectural Mantra**: *"Keep the synchronous financial path O(1) in DragonflyDB while projecting relational graph facts asynchronously in PostgreSQL."*

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-FRAUD-002`, `I-FRAUD-005` | `FraudRelationshipDaoIT.shouldPersistAndUpsertRelationships()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-FRAUD-003`, `I-FRAUD-003` | `GraphPatternEngineTest.shouldDetectTemporalCyclesWithoutFutureLeakage()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-FRAUD-004`, `REQ-FRAUD-005` | `GraphRiskCalculatorTest.shouldCalculateCompositeRiskScore()` | `TASK-2.3`, `TASK-2.4` |
| `REQ-FRAUD-006`, `I-FRAUD-007` | `DragonflyHotRiskMaterializerTest.shouldMaterializeGraphRisk()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-FRAUD-001`, `I-FRAUD-001` | `FraudGraphEventListenerIT.shouldConsumeNatsEventAndProjectGraph()` | `TASK-3.3`, `TASK-3.4` |
| `REQ-FRAUD-007` | `GraphRebuildServiceIT.shouldRebuildGraphAndHotStateFromEvents()` | `TASK-4.1`, `TASK-4.2` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Database Schema, Entities & Relational Graph Persistence
- [x] `TASK-1.1` [RED]: Create Flyway/schema migration `docker/init/schema.sql` and write failing integration test `FraudRelationshipDaoIT` testing entity creation, aggregate edge upsert (`ON CONFLICT`), and immutable event insertion.
- [x] `TASK-1.2` [GREEN]: Implement `FraudEntity`, `FraudRelationship`, `FraudRelationshipEvent`, `EntityType`, `RelationshipType` in `br.com.wallet.fraud.intelligence.domain` and `PostgresFraudRelationshipDao` in `br.com.wallet.fraud.intelligence.internal.persistence`.

### Phase 2: Graph Pattern Detection & Deterministic Scoring Engine
- [x] `TASK-2.1` [RED]: Write unit test `GraphPatternEngineTest` testing cycle detection ($A \to B \to C \to A$ within sliding window) and shared device clustering querying `fraud_relationship_events` with strict $G_{\leq t}$ cutoff.
- [x] `TASK-2.2` [GREEN]: Implement `GraphPatternEngine` with recursive CTE queries for cycle finding and connected component analysis.
- [x] `TASK-2.3` [RED]: Write unit test `GraphRiskCalculatorTest` verifying `GraphRiskSignals.calculateCompositeScore()` bounds scores in $[0.0, 1.0]$ with correct weights without magic numbers.
- [x] `TASK-2.4` [GREEN]: Implement `GraphRiskCalculator` combining signals into `graph_risk`.

### Phase 3: NATS Ingestion, Relational Projection & Dragonfly Hot Cache Feedback
- [x] `TASK-3.1` [RED]: Write unit test `DragonflyHotRiskMaterializerTest` checking `user:{id}:graph_risk` key formatting and 24h TTL.
- [x] `TASK-3.2` [GREEN]: Implement `HotRiskMaterializer` using `RedisAsyncCommands<String, String>`.
- [x] `TASK-3.3` [RED]: Write integration test `FraudGraphEventListenerIT` verifying that publishing `TransferCompletedEvent` to NATS triggers `RelationalGraphProjector`, updating PostgreSQL tables and DragonflyDB.
- [x] `TASK-3.4` [GREEN]: Implement `RelationalGraphProjector` and `FraudGraphConsumer` consuming `events.transfer` from NATS JetStream.

### Phase 4: Historical Replay, Rebuild & Architecture Convergence
- [x] `TASK-4.1` [RED]: Write integration test `GraphRebuildServiceIT` testing full reconstruction of `fraud_relationships` and Dragonfly hot cache from historical `fraud_relationship_events`.
- [x] `TASK-4.2` [GREEN]: Implement `GraphRebuildService`.
- [x] `TASK-4.3`: Verification of Spring Modulith boundaries and generation of `.spec/summaries/SUMMARY-000.5-hybrid-fraud-intelligence-and-relational-graph.md`.
