# 📝 Task Breakdown: TASKS-000.9.1 — Edge & Core Independent Runtimes & Process Separation

- **Associated Spec**: [`../SPEC-000.9.1-edge-core-independent-runtimes.md`](file:///.spec/SPEC-000.9.1-edge-core-independent-runtimes.md)
- **Associated Plan**: [`../plans/PLAN-000.9.1-edge-core-independent-runtimes.md`](file:///.spec/plans/PLAN-000.9.1-edge-core-independent-runtimes.md)
- **Status**: In Progress
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-PRC-001` (`I-PROCESS-001`) | `[MUST]` | `EdgeApplicationIT.shouldBootWithoutDatabase()` | `TASK-PRC-1.1`, `TASK-PRC-3.1` |
| `REQ-PRC-002` (`I-PORT-001`) | `[MUST]` | `CoreApplicationIT.shouldBootHeadlessOnPort8081()` | `TASK-PRC-1.2`, `TASK-PRC-4.1` |
| `REQ-PRC-003` (`I-STATE-001`) | `[MUST]` | `EdgeApplicationIT.assertNoDataSourceBeans()` | `TASK-PRC-3.1` |
| `REQ-PRC-004` (`I-CONTRACT-001`) | `[MUST]` | `ProcessBoundaryArchitectureTest.assertZeroCoreImports()` | `TASK-PRC-2.1` |
| `REQ-PRC-005` (`I-CONTRACT-001`) | `[MUST]` | `ProcessBoundaryArchitectureTest.assertEdgeDependsOnlyOnPublishedContracts()` | `TASK-PRC-2.1`, `TASK-PRC-2.2` |
| `REQ-PRC-006` (`I-DEDUP-001`) | `[MUST]` | `MultiProcessClusterIT.shouldDeliverEndToEndViaNats()` | `TASK-PRC-3.2`, `TASK-PRC-7.3` |
| `REQ-PRC-007` (`REQ-PRC-008`) | `[MUST]` | `MultiProcessClusterIT.shouldPushTerminalStatusViaNatsSse()` | `TASK-PRC-4.2`, `TASK-PRC-5.1` |
| `REQ-PRC-009` (`I-CONSUME-001`) | `[MUST]` | `CoreConsumerClusterIT.shouldLoadBalanceAcrossReplicas()` | `TASK-PRC-4.3` |
| `REQ-PRC-010` (`I-JOURNAL-001`) | `[MUST]` | `SpoolLockFileTest.shouldPreventConcurrentWriters()` | `TASK-PRC-3.3`, `TASK-PRC-3.3b` |
| `REQ-PRC-012` (`I-RUNTIME-001`) | `[MUST]` | `MonolithProfileIT.shouldEmitStartupWarning()` | `TASK-PRC-6.1` |
| `REQ-PRC-013` (`I-PORT-001`) | `[MUST]` | `PortSegregationIT.shouldVerifyPortSeparation()` | `TASK-PRC-3.4`, `TASK-PRC-4.1` |
| `REQ-PRC-014` (`I-GRACEFUL-001`) | `[MUST]` | `GracefulShutdownIT.shouldFlushBatchOnSigterm()` | `TASK-PRC-6.2` |
| `REQ-PRC-015` (`I-EDGE-004`) | `[MUST]` | `EdgeReadinessProbeIT.shouldReflectRecoveryStatus()` | `TASK-PRC-3.4` |
| `REQ-PRC-016` (`I-RESOURCE-001`) | `[MUST]` | `ResourceProfileTest.verifyIndependentThreadLimits()` | `TASK-PRC-3.4`, `TASK-PRC-4.1` |
| `REQ-PRC-020` (`I-EDGE-007`) | `[MUST]` | `DurableStatusRecoveryIT.shouldBootstrapTerminalStatusAfterDroppedLiveEvent()` | `TASK-PRC-5.2`, `TASK-PRC-5.3` |
| `REQ-PRC-021` (`I-STATUS-001`) | `[MUST]` | `OperationStatusMonotonicityTest.shouldRecognizeCompletedAsTerminal()` | `TASK-PRC-5.4` |
| `REQ-PRC-022` (`I-ACK-001`) | `[MUST]` | `CoreCommandConsumerAckTest.shouldAckOnlyAfterFinancialEffect()` | `TASK-PRC-6.3` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing a task, focus strictly on the active task card below. Do not load unrelated modules into memory.

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Build & Packaging Configuration ([MUST])
- [x] `TASK-PRC-1.1` [MUST]: Configure `:edge/build.gradle` with `bootJar { enabled = true; archiveFileName = 'wallet-edge.jar' }`, add `io.nats:jnats`, and assert absence of database starter dependencies.
- [x] `TASK-PRC-1.2` [MUST]: Configure root `build.gradle` to package `wallet-core.jar` as a headless Spring Boot application.

### Phase 2: Architectural Boundary Verification ([MUST])
- [x] `TASK-PRC-2.1` [MUST]: Implement `ProcessBoundaryArchitectureTest` using ArchUnit asserting `:edge` contains zero imports of `br.com.wallet.ledger.*`, `br.com.wallet.fraud.*`, `br.com.wallet.savings.*`, `br.com.wallet.goals.*`, or `javax.sql.*`/`jakarta.persistence.*`.
- [x] `TASK-PRC-2.2` [MUST]: Assert dependency direction `Edge → Contracts ← Core` (`assertEdgeDependsOnlyOnPublishedContracts()` and `assertCoreDoesNotExposeEdgeImplementation()`).

### Phase 3: Edge Independent Runtime & Single-Writer Spool ([MUST])
- [x] `TASK-PRC-3.1` [MUST]: Implement `br.com.wallet.edge.EdgeApplication` entry point with `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})`.
- [x] `TASK-PRC-3.2` [MUST]: Implement standalone `EdgeNatsConfiguration` and NATS publisher in `:edge` injecting `Nats-Msg-Id: <operationId>`.
- [x] `TASK-PRC-3.3` [MUST]: Implement `.spool.lock` exclusive locking mechanism (`FileChannel.tryLock()`) in `SegmentedFileJournal` to enforce `I-JOURNAL-001` single-writer isolation.
- [x] `TASK-PRC-3.3b` [MUST]: Implement `SpoolLockFileTest.shouldAllowIndependentWritersForDifferentDirectories()` proving independent Edge instances with distinct spool directories run concurrently without lock collisions.
- [x] `TASK-PRC-3.4` [MUST]: Configure `EdgeApplication` default ports (HTTP `8080`, UDP `8443` QUIC) and Actuator readiness probe reflecting `JournalRecoveryWorker` status.

### Phase 4: Core Headless Runtime & Status Publisher ([MUST])
- [x] `TASK-PRC-4.1` [MUST]: Implement `CoreRuntimeConfiguration` setting management port `8081` and disabling Edge WebFlux ingress routes when `wallet.runtime.mode=multi-process`.
- [x] `TASK-PRC-4.2` [MUST]: Implement `CoreStatusPublisher` publishing terminal operation status (`COMPLETED` / `FAILED`) to NATS JetStream `operations.status.<operationId>`.
- [x] `TASK-PRC-4.3` [MUST]: Update `CoreCommandConsumer` to broadcast status via `CoreStatusPublisher` upon use case execution completion.

### Phase 5: Status Fanout & Durable State Recovery ([MUST])
- [x] `TASK-PRC-5.1` [MUST]: Update `OperationStatusHub` in `:edge` to subscribe to NATS subject `operations.status.*` to receive distributed status transitions and broadcast to local SSE streams.
- [x] `TASK-PRC-5.2` [MUST]: Implement `DurableOperationStateProvider` SPI and `NatsDurableOperationStateProvider` in `:edge` with `CoreOperationQueryResponder` in Core over NATS Request-Reply `operations.query.<operationId>` (authoritative PostgreSQL resolution, returns `NOT_FOUND` if unrecorded, returns `DEGRADED_UNAVAILABLE` on timeout).
- [x] `TASK-PRC-5.3` [MUST]: Implement `DurableStatusRecoveryIT` verifying dropped status event is recoverable through durable status query, `NOT_FOUND` maps to in-flight processing, and timeout yields client-visible degraded response without hanging.
- [x] `TASK-PRC-5.3b` [MUST]: Reconcile `DurableStatusRecoveryIT` compile errors by binding `OperationQueryUseCase` mock to canonical `OperationStatusResponse` record (eliminating unresolved `OperationStatusView`).
- [x] `TASK-PRC-5.4` [MUST]: Implement `OperationStatusMonotonicityTest` and enforce terminal status monotonicity in `WalletOperationsDao` (status cannot regress from `COMPLETED` or `FAILED`).

### Phase 6: Dual-Profile Runtime & Graceful Termination ([MUST])
- [x] `TASK-PRC-6.1` [MUST]: Implement `wallet.runtime.mode` toggle (`multi-process` default, `monolith` test mode with startup warning log).
- [x] `TASK-PRC-6.2` [MUST]: Configure graceful shutdown (`server.shutdown=graceful`) with batch flush on SIGTERM (`I-GRACEFUL-001`).
- [x] `TASK-PRC-6.3` [MUST]: Implement `CoreCommandConsumerAckTest` asserting financial ACK durability (`I-ACK-001`): unprocessed commands are never ACKed before financial mutations commit durably.

### Phase 7: End-to-End Multi-Process Integration Tests ([MUST])
- [x] `TASK-PRC-7.1` [MUST]: Implement `EdgeStandaloneIT` testing standalone Edge boot, zero DB beans, and HTTP 202 acceptance.
- [x] `TASK-PRC-7.1b` [MUST]: Modernize `EdgeStandaloneIT` to use modern Spring Boot 4.1+ `RestTestClient` fluent API (`.post().uri(...).exchange().expectStatus().isAccepted()`), configuring `spring-boot-starter-webmvc-test` and `spring-boot-webtestclient` in `:edge/build.gradle`.
- [x] `TASK-PRC-7.2` [MUST]: Implement `CoreHeadlessIT` testing headless Core on port 8081 with NATS consumer.
- [x] `TASK-PRC-7.2b` [MUST]: Modernize `CoreHeadlessIT` to use modern Spring Boot 4.1+ `RestTestClient` fluent API (`.post().uri(...).exchange().expectStatus().isNotFound()`).
- [x] `TASK-PRC-7.2c` [MUST]: Eliminate premature Edge bean component-scanning in Core headless mode by removing `@Component` from `SegmentedFileJournal`, `IngressBulkhead`, `OperationStatusAuthorizationFilter`, and `AltSvcWebFilter` (instantiated solely via `EdgeConfiguration`), and gating `EdgeObservabilityConfig` and `EdgeNatsConfiguration` with `@ConditionalOnEdgeIngress`.
- [x] `TASK-PRC-7.3` [MUST]: Implement `MultiProcessClusterIT` executing end-to-end command dispatch scenario: `POST(opId=A) -> 202 -> Core processes A -> Ledger effect = 1 -> COMPLETED persisted -> status event dropped -> SSE reconnect -> operations.query(A) -> COMPLETED -> POST(opId=A) repeated -> idempotent replay (financial effects remain = 1)`.
- [x] `TASK-PRC-7.4` [MUST]: Configure development/test profile with headless Core defaults (`wallet.runtime.mode=multi-process`, `wallet.edge.enabled=false`) in `application-test.yml`, constrain `OperationsController` `/{operationId}` to UUID regex, add `@TestPropertySource` with isolated spool directory to `EdgeToCoreIntegrationTest`, and add `HttpRequestMethodNotSupportedException` (405) and `NoResourceFoundException` (404) handlers in `ApiExceptionHandler`.
- [x] `TASK-PRC-7.5` [MUST]: Eliminate test suite interference on `./gradlew test`: isolate Edge spool directory with `${random.uuid}` fallback in `EdgeConfiguration` and test profiles, remove `@Bean GenericContainer<?> nats()` in `IntegrationTestBase` to prevent premature container shutdown across cached test contexts, add bounded retry in `AbstractNatsConsumer.setupGeneralSubscription`, and isolate NATS Request-Reply query subjects in `DurableStatusRecoveryIT`.
- [x] `TASK-PRC-7.6` [MUST]: Refactor `TracingAspect` with `ObjectProvider<Tracer>` constructor and null-safe bypass when `Tracer` bean is absent, ensuring standalone `EdgeApplication` (`EdgeStandaloneIT`) boots cleanly without `UnsatisfiedDependencyException`.
- [x] `TASK-PRC-7.7` [MUST]: Remove invalid `asyncDispatch` call on `SseEmitter` in `EdgeToCoreIntegrationTest`, designate `DurableOperationStateAdapter` as `@Primary` for in-process monolith resolution, and make `CoreOperationQueryResponder` conditional on `multi-process` runtime mode.
- [x] `TASK-PRC-7.8` [MUST]: Refactor `EdgeOperationsController` endpoints to return `ResponseEntity<?>` directly leveraging virtual thread unmounting, ensuring `RestTestClient` in `EdgeStandaloneIT` immediately observes HTTP 202, `Location` header, and response payload without requiring async dispatch.

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

### 4.1 Architectural Invariant Gates (Histories 47, 48 & 49)
- [ ] Edge runtime can be started with Core completely unavailable
- [ ] Core runtime can be started without public Edge ingress
- [ ] Two Edge instances do not share a spool directory
- [ ] Duplicate operationId produces $\le 1$ financial effect
- [ ] Dropped status event is recoverable through durable status query; query timeout emits degraded response without hanging
- [ ] Core duplicate JetStream delivery does not create duplicate financial effect
- [ ] Terminal operation status is monotonic (`COMPLETED`/`FAILED` cannot be overwritten)
- [ ] Core does not ACK unprocessed commands before financial ledger commitment (`I-ACK-001`)

### 4.2 Standard Build & Packaging Gates
- [ ] `./gradlew :edge:bootJar` succeeds and generates `edge/build/libs/wallet-edge.jar`
- [ ] `./gradlew bootJar` succeeds and generates `build/libs/wallet-core.jar`
- [ ] `ProcessBoundaryArchitectureTest` passes with 0 violations (`I-CONTRACT-001`)
- [ ] `EdgeStandaloneIT` passes with zero database connections (`I-STATE-001`)
- [ ] `MultiProcessClusterIT` passes end-to-end command acceptance and SSE streaming
- [ ] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`)
- [ ] **Zero Spec-Drift Reconciliation (`I-SDD-003`)**: Code, schemas, and specifications match 100%
- [ ] Practical Verification Guide with seed data authored in `SUMMARY-000.9.1.md` (`I-SDD-002`)
