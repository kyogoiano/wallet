# 📐 Specification: SPEC-000.9 — Reactive Edge Gateway & Ingress Resilience

- **Status**: 🟢 **Ratified (Frontend SSE & QUIC HTTP/3 Included)**
- **Author**: Antigravity Edge Architecture & Financial Reliability Team
- **Date**: 2026-09-09
- **Target Release**: Wallet Service V4 — Phase 000.9
- **Bounded Context**: `:edge` (`br.com.wallet.edge`) (Dedicated Subproject & Decoupled Financial Command Ingress Boundary)
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly focused on ingress boundary and durable acceptance.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history35.txt`](file:///.histories/history35.txt) to [`.histories/history38.txt`](file:///.histories/history38.txt): Mandated durable acceptance semantics, preallocated segmented journal baseline, group commit fsync, bounded bulkheads, and storage corruption isolation.
  - **Frontend Review**: Rejected pure gRPC for web browsers (Envoy proxy tax); formalized HTTP/2 & HTTP/3 REST with Server-Sent Events (SSE) for 202 push notifications and QUIC for 0-RTT mobile connection migration.
- **Constitutional Constraints**:
  - `I-ATOMICITY-001`: Core ledger transactions remain single-boundary ACID operations.
  - `I-IDEMPOTENCY-001`: Client-provided `operation_id` governs end-to-end deduplication.
  - `I-DEDUP-001`: NATS message deduplication header `Nats-Msg-Id: <operationId>`.

---

## 1. Intent & Business Value

Financial command ingress cannot rely on in-process application threads or ephemeral RAM buffers. When a client receives `202 ACCEPTED`, the system has legally accepted financial liability.
This specification extracts the HTTP write path into an independent **Reactive Edge Gateway** (`:edge` subproject / `br.com.wallet.edge`). It decouples ingress from ledger DB connections, establishes non-blocking Netty I/O with bounded bulkheading, and guarantees durable acceptance via distributed broker quorum or a local append-only segmented spool journal with group-commit `fsync`.
For frontends, it eliminates polling via **Server-Sent Events (SSE)** for real-time operation transitions, while supporting **QUIC / HTTP/3** for 0-RTT connection establishment and zero-disruption mobile network migration.

---

## 2. Mathematical & System Invariants

- **`I-EDGE-001` (Durable Acceptance Semantics)**: `202 ACCEPTED` SHALL ONLY be returned after durable acceptance is acknowledged via:
  1. **Broker Quorum**: JetStream persistence acknowledgement satisfying configured stream durability policy.
  2. **Durable Spillover Journal**: Group-commit `FileChannel.force(false)` within a preallocated durable segment.
  $$\text{Response}(202) \iff \text{Ack}_{\text{broker}}(\text{DurabilityPolicy}) \lor \text{force}(\text{Batch}(\text{command}))$$
- **`I-EDGE-002` (Bounded Inflight Bulkheading)**: Edge nodes MUST enforce configurable concurrency bounds (`edge.bulkhead.max-inflight`, default 2,048):
  $$\text{inflightCommands} \le \text{MAX\_INFLIGHT}$$
  When saturated, the edge MUST shed load with `HTTP 429` (Rate Limited) or `HTTP 503` (Bulkhead Full).
- **`I-EDGE-003` (Effectively-Once Financial Effect)**: The Edge MUST preserve `operationId` without modification across all transport, retry, journaling, and recovery paths. The Core Ledger enforces the financial invariant:
  $$\forall \text{opId}, \quad \text{FinancialEffects}(\text{opId}) \le 1$$
- **`I-EDGE-004` (Crash Recovery Readiness Gate)**: An Edge node MUST remain `NOT_READY` until recovery scan is complete and replay workers are operational:
  $$\text{Readiness}(\text{Edge}) = \text{READY} \iff \text{RecoveryScanCompleted} \land \text{RecoveryPipelineOperational}$$
- **`I-EDGE-005` (Bounded Spool Watermarks & Safety Boundary)**: Spool storage enforces hysteresis thresholds:
  $$\text{Usage} \ge 95\% \implies \text{SATURATED} \to \text{RejectDegradedAcceptance} \to \text{HTTP } 503$$
  Recovery to accept degraded operations requires utilization to drop below $85\%$ to prevent oscillation.
- **`I-EDGE-006` (Local Durability Scope)**: Local journal fsync guarantees process and host restart durability, but does not independently provide physical node-destruction durability without underlying hardware/storage replication.
- **`I-EDGE-007` (Durable Operation Visibility)**: An accepted operation MUST have a durable status source independent of ephemeral in-memory SSE hub state. SSE is a notification mechanism, not the source of truth. Reconnecting or lagging clients MUST observe the latest durable state.
- **`I-EDGE-008` (Core Failure Isolation)**: Failure, saturation, or total unavailability of PostgreSQL, core ledger connection pools, or downstream consumers MUST NOT prevent the Edge from performing durable acceptance via NATS or the local spool journal, subject only to edge-local resource limits.

---

## 3. MoSCoW Requirements

### 3.1 Pillar A: Durable Acceptance & Envelope Safety [MUST]
- **`REQ-EDG-001` [MUST]**: Edge `CommandAcceptanceService` MUST return `202 ACCEPTED` only upon broker durable acknowledgement or local journal fsync completion.
- **`REQ-EDG-002` [MUST]**: In degraded mode (NATS timeout/down), edge MUST append command to preallocated `SegmentedFileJournal` and await group-commit before responding.
- **`REQ-EDG-003` [MUST]**: If both NATS and journal fail or spool reaches `SATURATED` ($\ge 95\%$), edge MUST reject with `HTTP 503 Service Unavailable` and `Retry-After: 5`.
- **`REQ-EDG-015` [MUST]**: Edge MUST enforce maximum command envelope size (`MAX_COMMAND_SIZE`, default 64KB). Requests exceeding limit MUST be rejected with `HTTP 413 Payload Too Large`.

### 3.2 Pillar B: Bounded Ingress & Rate Limiting [MUST]
- **`REQ-EDG-004` [MUST]**: L0/L1 lock-free token bucket MUST reject excess perimeter traffic in $O(1)$ allocation-free execution ($P99 < 10\mu s$) without external network calls.
- **`REQ-EDG-005` [MUST]**: Configurable Bulkhead MUST bound active concurrent command dispatches to `MAX_INFLIGHT`, returning `HTTP 429` on saturation.
- **`REQ-EDG-006` [MUST]**: Resilience4j Circuit Breaker guarding NATS publisher MUST transition to `OPEN` when error rate $> 10\%$ or $P99 > 50\text{ms}$.

### 3.3 Pillar C: Segmented Journal Framing & Group Commit [MUST]
- **`REQ-EDG-007` [MUST]**: `SegmentedFileJournal` preallocates 64MB segments with 32B Segment Headers and 54B Record Headers:
  ```text
  Segment Header: [Magic: 4B (0x57414C4A)][Version: 2B][SegmentId: 8B][CreatedAt: 8B][HeaderCRC32C: 4B][Padding: 6B]
  Record Frame:   [Magic: 4B][Version: 2B][Flags: 2B][Length: 4B][CRC32C: 4B][SeqNo: 8B][OpId: 16B][Timestamp: 8B][CmdType: 2B][PayloadLen: 4B][Payload: NB]
  ```
- **`REQ-EDG-008` [MUST]**: Group commit engine MUST batch concurrent writes, flushing when `batchSize >= MAX_BATCH_RECORDS` (100) OR `elapsed >= MAX_GROUP_COMMIT_DELAY` (1ms, measured from first write to empty batch), establishing a `force(false)` durability barrier before completing caller futures.

### 3.4 Pillar D: Crash Recovery, Fair Drain & Forensics [MUST]
- **`REQ-EDG-009` [MUST]**: Startup lifecycle MUST scan spool segments and schedule replay to NATS with `replayed: true` and `Nats-Msg-Id: <operationId>`.
- **`REQ-EDG-010` [MUST]**: Reactive health endpoint (`GET /actuator/health/readiness`, implementing Spring Boot `ReactiveHealthIndicator`) MUST emit `OUT_OF_SERVICE` during recovery scanning, switching to `UP` once operational.
- **`REQ-EDG-014` [MUST]**: When NATS is restored, Edge MUST drain spool records using configurable fair scheduling (`edge.recovery.replay-share=0.80`, `edge.recovery.live-reserved-share=0.20`), guaranteeing live traffic is never starved.
- **`REQ-EDG-016` [MUST]**: Journal records MUST remain recoverable until confirmed JetStream durable PUBACK. Records are never reclaimed on dispatch attempt.
- **`REQ-EDG-018` [MUST]**: CRC32C checksum failure MUST halt segment scan, preserve segment in forensic isolation (`.corrupt`), raise CRITICAL alert, and MUST NEVER enter the application DLQ.

### 3.5 Pillar E: Frontend Push & Multi-Protocol Transport (QUIC / HTTP/3 + SSE) [MUST]
- **`REQ-EDG-019` [MUST]**: Edge MUST provide an SSE stream (`GET /operations/{operationId}/stream`, `text/event-stream`) querying the latest durable operation state before subscribing to live transitions, pushing real-time status (`PROCESSING`, `COMPLETED`, `FAILED`) and completing immediately if already terminal (`I-EDGE-007`).
- **`REQ-EDG-020` [MUST]**: Edge MUST support HTTP/3 over QUIC with `Alt-Svc` header and HTTP/2 TLS fallback. 0-RTT MAY be used for connection establishment, but financial mutations (`POST /operations/*`) MUST require a 1-RTT handshake to prevent early-data replay.

### 3.6 Pillar F: Edge-to-Core Bridge & Command Consumer [MUST]
- **`REQ-EDG-021` [MUST]**: Production `EdgeCommandPublisher` (`NatsEdgeCommandPublisher` in `br.com.wallet.infrastructure.messaging.publisher`) MUST publish `CommandEnvelope` to NATS JetStream `commands` stream (`commands.wallet.<type>`), injecting `Nats-Msg-Id: <operationId>` (`I-DEDUP-001`, `I-IDEMPOTENCY-001`), trace context headers (`operation_id`, `type`, `client_ip`, `tenant_id`, `timestamp`), and overriding the fallback stub.
- **`REQ-EDG-022` [MUST]**: Dedicated `CoreCommandConsumer` (`br.com.wallet.infrastructure.messaging.consumer`) MUST subscribe to JetStream `commands.wallet.*`, deserialize payloads into domain records (`Transfer`, `Deposit`, `Withdraw`), dispatch to Core Use Cases (`TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`), and publish terminal status (`COMPLETED` / `FAILED`) to `OperationStatusHub` (`REQ-EDG-019`).

### 3.7 Operational Governance [SHOULD / COULD / WON'T]
- **`REQ-EDG-011` [SHOULD]**: Spool cleaner worker MUST prune fully acknowledged segments older than 24h.
- **`REQ-EDG-012` [COULD]**: Pluggable `DurableSpilloverJournal` SPI allowing benchmark-driven RocksDB/TidesDB integration.
- **`REQ-EDG-013` [WON'T]**: In-memory ring buffer (Disruptor) acceptance without disk fsync.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`ledger`** (Core) | Receives commands from Edge via `CoreCommandConsumer` | Duplicate credit/debit on recovery/retry | `I-EDGE-003` & `I-IDEMPOTENCY-001` (Effectively-once financial effect) |
| **`infrastructure`** (NATS) | JetStream publish/consume for `commands.wallet.*` | Broker timeout / consumer backlog | `I-EDGE-001` (Local journal absorbs traffic; bounded retries) |
| **`edge`** (`OperationStatusHub`) | Receives terminal status from `CoreCommandConsumer` | Dropped notifications / missed terminal push | Direct hub dispatch upon use case completion / failure (`REQ-EDG-022`) |
| **`frontend`** (Web/Mobile) | Real-time state transition tracking | High-frequency polling saturation | `REQ-EDG-019` (SSE reactive push stream delivers `COMPLETED`/`FAILED`) |
| **`network`** (Perimeter) | Mobile network drops (Wi-Fi to 5G) | Broken TCP connection, retry storm | `REQ-EDG-020` (QUIC connection migration maintains stream without retry) |

---

## 5. Mandatory Test Triad (`I-TDD-002`) & Failure Gates

| Requirement | 1. Positive Canonical Test | 2. Invalid Input / Boundary Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-EDG-001` (`I-EDGE-001`) | `EdgeIngressIT.shouldReturn202OnNatsAck()` | Broker down $\to$ `shouldReturn202OnJournalFsync()` | Uncommitted RAM $\to$ `assertNo202BeforeFsync()` |
| `REQ-EDG-003` (`I-EDGE-005`) | `EdgeCapacityIT.shouldSpoolAtNormalCapacity()` | Disk $\ge 95\% \to$ Reject degraded mode | Spool full + NATS down $\to$ `HTTP 503 Service Unavailable` |
| `REQ-EDG-005` (`I-EDGE-002`) | `EdgeBulkheadIT.shouldAcceptUnderLimit()` | Concurrency $>$ limit $\to$ `HTTP 429` | Memory limit breach $\to$ Immediate load shedding |
| `REQ-EDG-009` (`I-EDGE-004`) | `EdgeCrashRecoveryIT.shouldReplayBacklog()` | Corrupt CRC32C $\to$ Quarantine segment | Unfinished scan $\to$ Readiness probe returns 503 |
| `REQ-EDG-019` (SSE Stream) | `EdgeStreamIT.shouldPushStatusTransitions()` | Unknown operationId $\to$ 404 Not Found | Abrupt client disconnect $\to$ Clean resource release |
| `REQ-EDG-020` (QUIC / H3) | `EdgeTransportIT.shouldSupportHttp3AndAltSvc()`| Non-QUIC client $\to$ Fallback to HTTP/2 | UDP blocked $\to$ Seamless TLS TCP fallback |
| `REQ-EDG-021` (NATS Publisher) | `NatsEdgeCommandPublisherTest.shouldPublishWithMsgId()`| Unserializable payload $\to$ Exception | Missing JetStream Ack $\to$ Spillover fallback |
| `REQ-EDG-022` (Core Consumer) | `CoreCommandConsumerTest.shouldDispatchAndNotifyHub()`| Domain failure $\to$ Hub emits FAILED + Ack | Poison pill $\to$ Route DLQ without hub hang |

---

## 6. Acceptance Criteria

- [x] Ingress endpoints execute on non-blocking WebFlux with `CommandAcceptanceService` decoupling.
- [x] Zero `202 ACCEPTED` responses emitted without verified broker PUBACK or fsync completion (`I-EDGE-001`).
- [x] Real-time operation state pushed via Server-Sent Events (`REQ-EDG-019`).
- [x] Edge supports HTTP/3 (QUIC) with `Alt-Svc` headers and HTTP/2 TLS fallback (`REQ-EDG-020`).
- [x] Maximum command envelope size enforced at 64KB (`REQ-EDG-015`).
- [x] Binary journal records framed with `CommandType`, `SequenceNumber`, CRC32C, and Segment Headers (`REQ-EDG-007`).
- [x] Preallocated 64MB segments use `force(false)` for record group-commit and `force(true)` for segment creation.
- [x] Spool watermarks enforce hysteresis: reject at $\ge 95\%$, resume degraded acceptance below $< 85\%$.
- [x] Storage CRC corruption isolated to forensic incident quarantine without poison DLQ pollution (`REQ-EDG-018`).
- [x] Production JetStream publisher `NatsEdgeCommandPublisher` publishes commands with `Nats-Msg-Id` (`REQ-EDG-021`).
- [x] Resilient `CoreCommandConsumer` dispatches to Core Use Cases and pushes terminal status to `OperationStatusHub` (`REQ-EDG-022`).
