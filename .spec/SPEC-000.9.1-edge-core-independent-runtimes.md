# 📐 Specification: SPEC-000.9.1 — Edge & Core Independent Runtimes & Process Separation

- **Status**: 🟢 **Ratified (Post-Review Revision via History 48)**
- **Author**: Antigravity Edge & Core Reliability Architecture Team
- **Date**: 2026-09-11
- **Target Release**: Wallet Service V4 — Phase 000.9.1
- **Bounded Context**: `:edge` (`br.com.wallet.edge`), `:core` (`br.com.wallet.core`), and Root Runtime Orchestration
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly focused on process separation, contract boundaries, and runtime isolation.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history42.txt`](file:///.histories/history42.txt) to [`.histories/history45.txt`](file:///.histories/history45.txt): Proved Spring Modulith alone provides logical modularity but zero runtime crash isolation or independent scaling. Established NATS JetStream as the scaling fabric ($N$ Edge $\leftrightarrow$ $M$ Core).
  - [`.histories/history46.txt`](file:///.histories/history46.txt) & [`.histories/history48.txt`](file:///.histories/history48.txt): Architecture reviews refining runtime portability, graceful shutdown, node-local journal ownership, canonical status subject hierarchy, authoritative durable status resolution, and bounded degraded fallback.
  - [`SUMMARY-000.9`](file:///.spec/summaries/SUMMARY-000.9-reactive-edge-gateway-and-ingress-resilience.md): Established reactive ingress, journal fsync, JetStream publisher, and payload fingerprint conflict detection (`409 OPERATION_CONFLICT`).
- **Constitutional Constraints**:
  - `I-LEDGER-001` & `I-ATOMICITY-001`: Core ledger transactions remain single-boundary ACID operations.
  - `I-IDEMPOTENCY-001`: Client-provided `operation_id` governs end-to-end deduplication and payload fingerprint verification across process boundaries.
  - `I-EDGE-001` & `I-EDGE-008`: Edge durable acceptance functions independently of Core database availability.

---

## 1. Intent & Business Value

In `SPEC-000.9`, `:edge` was extracted as a Gradle subproject, but both Edge and Core still execute within a single JVM process (`WalletApplication`). Consequently, an OutOfMemoryError, garbage collection storm, database connection exhaustion, or crash in Core directly terminates Edge ingress.
`SPEC-000.9.1` decouples Edge and Core into **independently executable OS runtimes** (`EdgeApplication` and `CoreApplication` / root `WalletApplication`). Edge runs as a lean perimeter gateway with zero relational database connections, while Core operates as a headless financial transactional engine. NATS JetStream acts as the resilient asynchronous IPC fabric. `I-IDEMPOTENCY-001` remains authoritative for operation identity and canonical payload fingerprinting (`409 OPERATION_CONFLICT` on mismatched payloads for identical `operation_id`). Dual-profile execution is supported: independent OS processes in production and an optional composite JVM mode for local development. Platform-specific container orchestrations (RKE2, Rancher, vCluster, GKE) are strictly deferred to `SPEC-000.9.2`.

---

## 2. Mathematical & System Invariants

- **`I-PROCESS-001` (Process & Crash Isolation)**: Edge and Core execute in separate OS processes. A fatal crash, deadlock, or OOM in Core MUST NOT directly terminate Edge. Edge MAY degrade only according to its own admission, journal, and capacity policies:
  $$\text{Crash}(\text{Core}) \not\to \text{Crash}(\text{Edge})$$
- **`I-CONTRACT-001` (Zero Internal Leakage / Stable Command Contract)**: Edge MUST depend exclusively on exported command/status contracts (`CommandEnvelope`, `CommandType`, `OperationStatusResponse`, `DurableOperationStatus`). Edge classpath MUST NOT include JDBC drivers, PostgreSQL dependencies, Hibernate, or internal Core domain entities:
  $$\text{Deps}(\text{Edge}) \cap \{\text{ledger.internal}, \text{fraud.internal}, \text{persistence}, \text{JDBC}\} = \emptyset$$
- **`I-RUNTIME-001` (Dual-Profile Semantics Equivalence)**: Domain command semantics MUST remain equivalent between `multi-process` and `monolith` profiles; transport, timing, and failure semantics MAY differ. `monolith` profile is strictly for local dev/testing and MUST emit a warning log upon startup:
  $$\text{DomainSemantics}(\text{Edge}_{\text{multi-process}}) \equiv \text{DomainSemantics}(\text{Edge}_{\text{monolith}})$$
- **`I-RUNTIME-002` (Portable Runtime Contract)**: Edge and Core MUST be distributable as OCI-compatible container images and MUST NOT depend on a specific container runtime, Kubernetes distribution, cloud provider, or orchestration control plane:
  $$\text{Deps}(\text{Runtime}) \cap \{\text{KubernetesClient}, \text{VendorCloudAPI}\} = \emptyset$$
- **`I-PORT-001` (Network & Ingress Segregation)**: Edge binds public ingress ports (HTTP 8080 / UDP 8443 QUIC). Core MUST NOT expose public HTTP ingress, exposing only management/actuator endpoints on internal port 8081:
  $$\text{Port}(\text{Edge}_{\text{public}}) \in \{8080, 8443\}, \quad \text{Port}(\text{Core}_{\text{mgmt}}) = 8081, \quad \text{PublicRoutes}(\text{Core}) = \emptyset$$
- **`I-STATE-001` (Edge Relational-DB Independence)**: Edge maintains zero relational database connection pools, relying solely on local segmented journal files and NATS JetStream:
  $$\text{DBPoolSize}(\text{Edge}) = 0, \quad \text{ActiveSQLConnections}(\text{Edge}) = 0$$
- **`I-SCALE-001` (Horizontal Financial Correctness Independence)**: Edge and Core MUST support multiple concurrent instances ($N_{\text{edge}} \ge 1, M_{\text{core}} \ge 1$) without requiring instance affinity or shared in-memory state for financial correctness; node-local caches and connections may vary:
  $$\text{FinancialCorrectness}(\text{Cluster}) \perp (\text{InstanceAffinity} \lor \text{SharedInMemState})$$
- **`I-CONSUME-001` (Distributed Command Consumption)**: Core instances MUST safely consume commands concurrently from JetStream competing consumer groups without duplicate financial effects (`I-IDEMPOTENCY-001`, `Nats-Msg-Id`).
- **`I-JOURNAL-001` (Single-Writer Spool Isolation)**: Each Edge instance MUST exclusively own its local journal directory. A journal directory MUST NOT be concurrently shared between Edge instances. Platform-specific volume mappings are deferred to `SPEC-000.9.2`:
  $$\text{ConcurrentWriters}(\text{SpoolDirectory}) = 1$$
- **`I-RESOURCE-001` (Independent Resource Bounds)**: Edge and Core MUST expose independently configurable CPU, memory, concurrency, and connection-pool limits, requiring zero shared process-wide pools.
- **`I-STATUS-001` (Terminal Status Monotonicity)**: Terminal states (`COMPLETED`, `FAILED`) cannot regress or be mutated by duplicate commands or late out-of-order events. `FAILED` represents terminal domain rejection or DLQ exhaustion; transient infrastructure errors trigger retry and retain `PROCESSING` state:
  $$\text{State}(op) \in \{\text{COMPLETED}, \text{FAILED}\} \implies \Delta\text{State}(op) = \emptyset$$
- **`I-ACK-001` (Financial ACK Durability)**: Core MUST NEVER acknowledge (`ack()`) a financial command message from JetStream until all associated ledger mutations and balance adjustments are durably committed in the database (`I-LEDGER-001`). This applies across normal processing, crashes, exceptions, shutdown, and consumer rebalancing:
  $$\text{ACK}(msg) \implies \text{Committed}(\text{LedgerEffect})$$

---

## 3. MoSCoW Requirements

### 3.1 Pillar A: Process & Failure Isolation [MUST]
- **`REQ-PRC-001` [MUST]**: Subproject `:edge` MUST define its own Spring Boot application entry point (`br.com.wallet.edge.EdgeApplication`) producing an executable `bootJar` (`wallet-edge.jar`).
- **`REQ-PRC-002` [MUST]**: Core / root application MUST execute as an independent process (`wallet-core.jar` or headless `WalletApplication`) without registering Edge HTTP controllers, Netty web filters, or WebFlux ingress routers.
- **`REQ-PRC-003` [MUST]**: `EdgeApplication` MUST exclude relational database auto-configurations (`DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `DataSourceTransactionManagerAutoConfiguration`) and operate with zero JDBC connections (`I-STATE-001`).

### 3.2 Pillar B: Contract & Dependency Isolation [MUST]
- **`REQ-PRC-004` [MUST]**: Command and event envelopes (`CommandEnvelope`, `CommandType`, `OperationStatusResponse`, `DurableOperationStatus`) MUST reside in a clean published contract boundary (`:edge:api` or `:contracts`) with zero transitive DB dependencies.
- **`REQ-PRC-005` [MUST]**: Architecture test (`ProcessBoundaryArchitectureTest`) MUST assert that `:edge` contains zero imports of `br.com.wallet.ledger.*`, `br.com.wallet.fraud.*`, `br.com.wallet.savings.*`, `br.com.wallet.goals.*`, or `javax.sql.*`/`jakarta.persistence.*`, and that Edge depends only on published contracts (`Edge → Contracts ← Core`).

### 3.3 Pillar C: Durable Asynchronous IPC & Status Recovery [MUST]
- **`REQ-PRC-006` [MUST]**: In `multi-process` mode, Edge dispatches commands to Core strictly over NATS JetStream `commands.wallet.<type>` with `Nats-Msg-Id: <operationId>` (`I-DEDUP-001`).
- **`REQ-PRC-007` [MUST]**: `CoreCommandConsumer` in Core process MUST consume commands, execute transactional use cases, and broadcast terminal status (`COMPLETED` / `FAILED`) exclusively to canonical NATS subject `operations.status.<operationId>`.
- **`REQ-PRC-008` [MUST]**: Edge `NatsOperationStatusListener` MUST subscribe to canonical subject `operations.status.*` over NATS to dynamically stream state transitions to connected SSE clients across any active Edge node (`I-EDGE-007`).
- **`REQ-PRC-020` [MUST]**: In `multi-process` mode, Edge MUST query durable operation state from Core via NATS Request-Reply on `operations.query.<operationId>` before establishing the SSE live stream. Core resolves state authoritatively from PostgreSQL `wallet_operations`. If unrecorded, Core returns `NOT_FOUND` (state currently unavailable; Edge presents `PROCESSING / UNKNOWN` and streams live updates). If the query times out after bounded retry, Edge MUST emit an explicit `DEGRADED_UNAVAILABLE` event and MUST NOT silently claim state is current (`I-EDGE-007`).
- **`REQ-PRC-021` [MUST]**: Core and Edge MUST enforce terminal operation status monotonicity: duplicate command execution or late out-of-order events MUST NOT overwrite `COMPLETED` or `FAILED` states (`I-STATUS-001`).

### 3.4 Pillar D: Horizontal Scaling & Node-Local Spool [MUST]
- **`REQ-PRC-009` [MUST]**: Core consumer instances MUST join a shared JetStream durable consumer group (competing consumers), dynamically load-balancing commands across active Core instances without message duplication (`I-CONSUME-001`).
- **`REQ-PRC-010` [MUST]**: Edge spool journal MUST enforce single-writer directory ownership (`FileChannel.tryLock()`); restart recovery scans exclusively its own volume (`I-JOURNAL-001`).

### 3.5 Pillar E: Runtime Profiles & Container Portability [MUST]
- **`REQ-PRC-011` [MUST]**: Edge and Core MUST produce OCI-compliant artifacts runnable on any container platform without proprietary orchestrator APIs (`I-RUNTIME-002`).
- **`REQ-PRC-012` [MUST]**: Support configuration toggle `wallet.runtime.mode` (`multi-process` production default; `monolith` development/test profile with explicit startup warning log).
- **`REQ-PRC-013` [MUST]**: Edge process binds public HTTP `8080` (and `8443` QUIC); Core process sets `server.port=8081` with only health and metrics actuator endpoints enabled (`I-PORT-001`).

### 3.6 Pillar F: Lifecycle & Resource Governance [MUST]
- **`REQ-PRC-014` [MUST]**: Both Edge and Core MUST support graceful shutdown on SIGTERM within a configurable termination window: Edge marks readiness `OUT_OF_SERVICE`, sheds ingress, flushes in-flight group commits, and halts; Core ceases polling, finishes in-flight transactions, ACKs completed records, and exits (`I-GRACEFUL-001`).
- **`REQ-PRC-015` [MUST]**: Standardized Spring Boot Actuator readiness (`/actuator/health/readiness`) and liveness (`/actuator/health/liveness`) probes: Edge readiness reflects journal recovery completion (`I-EDGE-004`); Core readiness reflects DB schema compatibility and NATS consumer subscription.
- **`REQ-PRC-016` [MUST]**: Edge and Core MUST expose independently configurable CPU, memory, thread pool, and connection limits without shared process-wide pools (`I-RESOURCE-001`).
- **`REQ-PRC-022` [MUST]**: Core JetStream consumer MUST NOT acknowledge (`ack()`) messages before financial ledger mutations commit durably across all failure modes and lifecycles (`I-ACK-001`).

### 3.7 Operational Governance [SHOULD / COULD / WON'T]
- **`REQ-PRC-017` [SHOULD]**: Propagate W3C traceparent and `operation_id` baggage in NATS message headers between Edge and Core processes (`I-OBS-001`).
- **`REQ-PRC-018` [COULD]**: Support Unix Domain Socket (UDS) IPC for NATS connections when Edge and Core run on the same physical host.
- **`REQ-PRC-019` [WON'T]**: Direct synchronous HTTP/REST or RPC calls between Edge and Core processes in production.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`edge`** (`EdgeApplication`) | Public ingress HTTP/QUIC | Core process crash or GC pause | `I-PROCESS-001`: Edge continues accepting, spools to local journal |
| **`ledger`** (`CoreApplication`) | Headless command execution | Slow DB write path causes backpressure | `I-STATE-001` & `I-CONSUME-001`: JetStream buffers commands without Edge stalls |
| **`infrastructure`** (NATS) | IPC bus (`commands.*`, `operations.status.*`) | Message loss across network boundary | `I-CONTRACT-001` & `I-DEDUP-001`: JetStream at-least-once delivery |
| **`frontend`** (SSE) | Push notifications via `OperationStatusHub` | SSE client connected to Edge-1, Core completes on Core-2 | `REQ-PRC-008`: Canonical subject `operations.status.*` fanout delivers status to all Edge SSE hubs |
| **`platform`** (Lifecycle) | Container SIGTERM / rolling restarts | Hard kill terminates active transaction or fsync | `REQ-PRC-014` & `REQ-PRC-022`: Graceful shutdown flushes batch; `I-ACK-001` forbids premature ACK |

---

## 5. Mandatory Test Triad (`I-TDD-002`) & Failure Gates

| Requirement | 1. Positive Canonical Test | 2. Invalid Input / Boundary Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-PRC-001` (`I-PROCESS-001`) | `EdgeApplicationIT.shouldBootWithoutDatabase()` | Misconfigured DB URL $\to$ Ignored, boots green | Presence of `DataSource` bean $\to$ Context fail |
| `REQ-PRC-002` (`I-PORT-001`) | `CoreApplicationIT.shouldBootHeadlessOnPort8081()` | Accessing `/operations/*` on Core $\to$ 404 | Core binding to 8080 $\to$ Startup abort |
| `REQ-PRC-005` (`I-CONTRACT-001`) | `ProcessBoundaryArchitectureTest.assertZeroCoreImports()` | Edge importing `LedgerDao` $\to$ Build fail | Edge depending on `spring-boot-starter-data-jpa` $\to$ Fail |
| `REQ-PRC-007` (`REQ-PRC-008`) | `MultiProcessFlowIT.shouldDeliverEndToEndViaNats()` | Core slow/paused $\to$ Edge returns 202 immediately | Duplicate subject publication $\to$ Test fail |
| `REQ-PRC-012` (`I-RUNTIME-001`) | `MonolithProfileIT.shouldExecuteInSingleJvm()` | Mode invalid $\to$ Fail-fast configuration error | Monolith leaking DB transactions to Edge $\to$ Fail |
| `REQ-PRC-014` (Graceful Shutdown) | `GracefulShutdownIT.shouldFlushBatchOnSigterm()` | Kill -9 $\to$ Next pod start executes journal replay | Hanging shutdown $\to$ Force kill at termination timeout |
| `REQ-PRC-020` (`I-EDGE-007`) | `DurableStatusRecoveryIT.shouldBootstrapTerminalStatusAfterDroppedLiveEvent()` | Query timeout $\to$ Emits `DEGRADED_UNAVAILABLE` event | Silently falling back to live-only $\to$ Test fail |
| `REQ-PRC-021` (`I-STATUS-001`) | `OperationStatusMonotonicityTest.shouldEnforceTerminalState()` | Duplicate command arrival $\to$ Status unchanged | Attempting FAILED after COMPLETED $\to$ Rejected |
| `REQ-PRC-022` (`I-ACK-001`) | `CoreCommandConsumerAckTest.shouldAckOnlyAfterFinancialEffect()` | Transient failure $\to$ NAK with retry delay | ACK before DB commit $\to$ Invariant breach |

---

## 6. Acceptance Criteria

- [ ] Subproject `:edge` compiles and packages into an independent runnable bootJar (`wallet-edge.jar`).
- [ ] Subproject `:edge` runs cleanly with zero relational database beans or HikariCP connection pools (`I-STATE-001`).
- [ ] Core runs headless with management server on port `8081` and zero public command endpoints (`I-PORT-001`).
- [ ] `ProcessBoundaryArchitectureTest` verifies strict dependency encapsulation between `:edge` and Core (`I-CONTRACT-001`).
- [ ] Commands dispatched to Edge are received by Core via JetStream `commands.wallet.*` with `Nats-Msg-Id` deduplication.
- [ ] Operation status transitions published by Core are delivered across canonical subject `operations.status.<operationId>` to Edge SSE clients (`REQ-PRC-008`).
- [ ] Edge resolves durable operation status from Core's authoritative store via NATS Request-Reply without database access, returning degraded status on timeout (`REQ-PRC-020`).
- [ ] Operation status transitions are strictly monotonic; terminal statuses cannot regress (`REQ-PRC-021`).
- [ ] Graceful shutdown hook flushes active group commit batch and Core never ACKs commands before financial commitment (`REQ-PRC-014`, `REQ-PRC-022`, `I-ACK-001`).
- [ ] Monolith profile emits an explicit startup warning log (`REQ-PRC-012`).
- [ ] Total lines in this specification do not exceed 250 lines (`I-SDD-006`).
