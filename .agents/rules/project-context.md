---
name: project-context
description: Architecture overview, module boundaries, technology stack, and runtime topology for Wallet Service.
trigger: always_on
---

# 🌐 Wallet Service — Project Context

## 1. System Architecture (Spring Modulith DAG)

The Wallet Service follows **Modular Monolith (Spring Modulith)** and **Clean Architecture / DDD** principles:

```
┌────────────────────────────────────────────────────────────────────────┐
│                      br.com.wallet.infrastructure                      │
│             (REST Controllers, NATS JetStream, Config)                 │
└───────┬──────────────┬───────────────┬────────────────┬────────────────┘
        │              │               │                │
        ▼              ▼               ▼                ▼
┌──────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────────┐
│br.com.wallet.│ │br.com.wallet│ │br.com.wallet│ │br.com.wallet.│
│   savings    │ │    goals    │ │     dlq     │ │    ledger    │
└───────┬──────┘ └──────┬──────┘ └──────┬──────┘ └──────┬───────┘
        │               │               │               │
        │ observes      │ evaluates     │ recovers      │ depends on
        ▼               ▼               ▼               ▼
┌───────────────────────────────────────────────────────────────┐
│                      br.com.wallet.fraud                      │
│ (Fusion Gate, Rules, Intelligence, Propagation, Vectors, SLM) │
└───────────────────────────────┬───────────────────────────────┘
                                │ depends on
                                ▼
┌───────────────────────────────────────────────────────────────┐
│                      br.com.wallet.core                       │
│     (TraceContext, FraudContext, Exceptions, Traceable)       │
└───────────────────────────────────────────────────────────────┘
```

### Module Structure
- **`:core` (`br.com.wallet.core`)**: Shared foundational types: `TraceContext`, `FraudContext` (implements `TraceContext`), `OperationOrigin`, `Traceable`, `TracingAspect`, `IdempotencyException`, `AccountBlockedException`. Zero outgoing dependencies.
- **`:fraud` (`br.com.wallet.fraud`)**: Anti-fraud & risk scoring engine:
  - `fusion`: `FraudGate` pre-execution evaluation ($P99 < 2\text{ms}$ hot cache lookup in DragonflyDB), multi-signal fusion, micro-ML ONNX scoring, and LangGraph agentic investigation workflows.
  - `rules`: Deterministic rules (`UserBlockRule`, `GlobalVelocityRule`), Caffeine local sliding windows, and Dragonfly velocity counters.
  - `intelligence`: Two-tier relational graph projection (`fraud_relationships` + `fraud_relationship_events`) and cycle detection.
  - `propagation`: Path-influence risk propagation with temporal exponential decay and PostgreSQL `SKIP LOCKED` job queue.
  - `embeddings`: 16-dimensional behavioral profile vectorization and archetype centroid matching via PostgreSQL `pgvector`.
  - `investigation`: Air-gapped investigation synthesizer with `LocalInferenceClient` SPI (Ollama SLM), PII masking, and claim grounding validation.
- **`br.com.wallet.ledger` (in root)**: Transactional ledger & core banking domain (`TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`, `BalanceUseCase`, `ValidateLedgerUseCase`, `AccountStateUseCase`), `AccountDao`, `LedgerDao`, `OutboxDao`, and `FraudCheckHelper`.
- **`br.com.wallet.savings` (in root)**: Smart Savings capability module (`SavingsPlanUseCase`, `SavingsQueryUseCase`, `SavingsRuleEngine`, `SavingsEventListener`) reacting to banking events via `@ApplicationModuleListener`.
- **`br.com.wallet.goals` (in root)**: Financial Goal & Cashflow Strategy Engine (`GoalUseCase`, `GoalStrategyEngine`, `ContributionCalculator`, `CashflowCapacityCalculator`) providing deterministic feasibility simulations and multi-goal waterfall prioritization.
- **`br.com.wallet.dlq` (in root)**: Dead Letter Queue resilience capability module (`DlqManagementUseCase`, `DlqQueryUseCase`, `DlqReplayEngine`, `DlqOperationsDao`), enforcing bounded replays capped at 3 retries, transition to `EXHAUSTED`, and operator REST endpoints.
- **`:edge` (`br.com.wallet.edge`)**: Reactive Edge Gateway & Ingress Resilience subproject:
  - `command`: `CommandAcceptanceService`, `CommandEnvelope`, `CommandType`.
  - `resilience`: `PerimeterRateLimiter` ($P99 < 10\mu s$ token bucket), `IngressBulkhead` (default 2048), `BrokerCircuitBreaker`.
  - `journal`: Preallocated 64MB `SegmentedFileJournal`, `BinaryRecordCodec` (54B record / 32B segment headers, CRC32C), `GroupCommitEngine` (100-batch / 1ms `force(false)`), `SpoolWatermarkGate` (95/85% hysteresis).
  - `ingress` & `transport`: `EdgeOperationsController`, `EdgeOperationsStreamController` (Server-Sent Events), `AltSvcWebFilter` (HTTP/3 over QUIC on UDP 8443).
  - `recovery`: `JournalRecoveryWorker` (80/20 fair drain), `SpoolAckTracker`, and `EdgeReadinessHealthIndicator` (`ReactiveHealthIndicator` emitting `Mono<Health>`).
- **`br.com.wallet.infrastructure` (in root)**: REST controllers (`TransferController`, `DepositController`, `WithdrawController`, `SavingsController`, `GoalController`, `DlqController`, `FraudInvestigationController`), NATS JetStream workers, and Spring configuration.

---

## 2. Technology Stack

- **Runtime & Language**: Java 27, Spring Boot 4.2.0-M1, Gradle 9.8-rc-1
- **Database**: PostgreSQL 17/19 with schema migrations in `docker/init/schema.sql` and `pgvector` extension (`vector(16)`, `vector(128)`).
- **In-Memory Store & Distributed State**: DragonflyDB v1.40.1 (multi-threaded, Redis-compatible, RESP3, Epoll Unix Domain Sockets `/var/run/redis/redis.sock` & TCP `6379` fallback).
- **Messaging & Event Streaming**: NATS JetStream (`events.*`, `commands.*`, `commands.dlq.*`)
- **Micro-ML & Local SLM**: ONNX Runtime Java for shadow behavioral risk scoring; local containerized Ollama for evidence-grounded investigation narratives.
- **Observability**: OpenTelemetry Java SDK, OpenObserve OTLP backend (Traces, Metrics, Logs) with `operation_id` baggage propagation.
- **Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL with pgvector, DragonflyDB, NATS).

---

## 3. Data & Storage Model

1. **`accounts`**: Fast projection table holding current balances and lifecycle status (`ACTIVE`, `BLOCKED`, `SUSPENDED`, `FROZEN`). Updated synchronously with `SELECT FOR UPDATE` locking.
2. **`ledger`**: Append-only tamper-evident log containing cryptographic SHA-256 hash chains (`hash_n = SHA256(...)`).
3. **`outbox`**: Transactional outbox table storing serialized domain events for asynchronous relay.
4. **`dlq_operations`**: Dead-letter storage tracking retry counts, failure categories, and statuses (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `EXHAUSTED`, `DISCARDED`).
5. **`savings_plans` / `savings_rules` / `savings_executions`**: Smart savings plan configurations and sweep execution history.
6. **`goals` / `cashflow_profiles`**: Financial goals, target horizons, and user cashflow capacity profiles.
7. **`fraud_relationships` / `fraud_relationship_events`**: Relational entity graph facts, topologies, and temporal interaction records.
8. **`fraud_entity_features`**: PostgreSQL `pgvector(16)` behavioral profile vectors and magnitude scalar.
9. **`fraud_propagation_jobs` / `fraud_embedding_jobs`**: Hand-rolled PostgreSQL `SKIP LOCKED` durable asynchronous task queues.
10. **DragonflyDB**: High-speed ephemeral state:
    - `risk_profile:USER:{id}`: Hash containing `graph_risk`, `temporal_risk`, `vector_risk`, `fused_score`, and status.
    - `user:{id}:graph_risk`, `user:{id}:temporal_risk`: Materialized risk metrics for sub-millisecond fraud gate evaluation.
    - Sliding windows and rate-limiting counters evaluated atomically via Lua scripts.

---

## 4. Key Request Flows

- **Write Path (Transfer/Deposit/Withdraw)**:
  1. API Controller receives request with `Idempotency-Key` / `operation_id`.
  2. **Fraud Gate Evaluation**:
     - `FraudGate.evaluateAuthorization(userId, amount)` queries DragonflyDB hot cache ($P99 < 2\text{ms}$). Rejects with `FraudBlockedException` if `decision == HARD_BLOCK`.
     - Legacy/Hot velocity rules evaluate local Caffeine window & Dragonfly sliding counters via atomic Lua scripts.
  3. Domain Use Case locks participating accounts in deterministic lexicographical order (`SELECT FOR UPDATE`).
  4. Account lifecycle checked: participating accounts must be `ACTIVE` (`I-ACCOUNT-001`).
  5. Balance checked $\rightarrow$ account balances updated $\rightarrow$ ledger entry inserted with next cryptographic hash $\rightarrow$ outbox event recorded.
  6. Transaction commits atomically.
  7. In-process Modulith listeners (`@ApplicationModuleListener`) notify `savings` and `goals` without blocking the ledger transaction.
  8. Outbox Relay scans pending outbox events every 10s and publishes to NATS JetStream with exponential backoff retries.

