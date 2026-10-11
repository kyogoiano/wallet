# 📝 Task Breakdown: TASKS-000.9 — Reactive Edge Gateway & Ingress Resilience

- **Associated Spec**: [`../SPEC-000.9-reactive-edge-gateway-and-ingress-resilience.md`](file:///.spec/SPEC-000.9-reactive-edge-gateway-and-ingress-resilience.md)
- **Associated Plan**: [`../plans/PLAN-000.9-reactive-edge-gateway-and-ingress-resilience.md`](file:///.spec/plans/PLAN-000.9-reactive-edge-gateway-and-ingress-resilience.md)
- **Status**: 🟢 **Completed & Verified**
- **Execution Order**: Prioritize `[MUST]` tasks in strict Red $\to$ Green $\to$ Refactor sequence (`I-TDD-001`, `I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :---: | :--- | :--- |
| `REQ-EDG-001`, `I-EDGE-001` | `[MUST]` | `CommandAcceptanceServiceTest.shouldReturn202OnBrokerAck()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-EDG-002`, `I-EDGE-001` | `[MUST]` | `CommandAcceptanceServiceTest.shouldReturn202OnJournalFsyncWhenBrokerDown()` | `TASK-2.2`, `TASK-3.2` |
| `REQ-EDG-003`, `I-EDGE-005` | `[MUST]` | `EdgeCapacityIT.shouldReturn503WhenSpoolSaturated()` | `TASK-2.3`, `TASK-6.2` |
| `REQ-EDG-004` | `[MUST]` | `PerimeterRateLimiterTest.shouldShedExcessPerimeterTraffic()` | `TASK-3.3` |
| `REQ-EDG-005`, `I-EDGE-002` | `[MUST]` | `IngressBulkheadTest.shouldRejectWhenMaxInflightExceeded()` | `TASK-3.4` |
| `REQ-EDG-006` | `[MUST]` | `BrokerCircuitBreakerTest.shouldTripOnHighLatencyOrErrors()` | `TASK-3.5` |
| `REQ-EDG-007` | `[MUST]` | `BinaryRecordCodecTest.shouldEncodeAndDecodeWithCrc32cAndCommandType()` | `TASK-1.1`, `TASK-1.2`, `TASK-1.4`, `TASK-1.5` |
| `REQ-EDG-008` | `[MUST]` | `GroupCommitEngineTest.shouldBatchFsyncWithinTimeWindow()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-EDG-009`, `I-EDGE-003` | `[MUST]` | `JournalRecoveryWorkerTest.shouldRecoverAndDrainBacklog()` | `TASK-4.1`, `TASK-4.2` |
| `REQ-EDG-010`, `I-EDGE-004` | `[MUST]` | `EdgeReadinessHealthIndicatorTest.shouldEmitOutOfServiceWhenInitializing()`| `TASK-4.3` |
| `REQ-EDG-014` | `[MUST]` | `JournalRecoveryWorkerTest.shouldFairDrainWithoutStarvation()`| `TASK-4.1` |
| `REQ-EDG-015` | `[MUST]` | `EdgeRequestValidatorTest.shouldRejectOversizedPayloadWith413()` | `TASK-3.6`, `TASK-1.5` |
| `REQ-EDG-016` | `[MUST]` | `SpoolAckTrackerTest.shouldOnlyReclaimAfterPubAck()` | `TASK-4.4`, `TASK-4.5`, `TASK-4.6` |
| `REQ-EDG-018` | `[MUST]` | `EdgeCorruptionIT.shouldHaltOnCrcFailureAndIsolateSegment()` | `TASK-6.3`, `TASK-4.2` |
| `REQ-EDG-019`, `I-EDGE-007` | `[MUST]` | `EdgeStreamIT.shouldReplayCurrentStatusOnConnect()` | `TASK-5.1`, `TASK-5.2`, `TASK-5.6`, `TASK-5.7` |
| `REQ-EDG-019`, Multi-Node | `[MUST]` | `EdgeStreamClusterIT.shouldDeliverStatusAcrossEdgeNodes()` | `TASK-5.8`, `TASK-5.9` |
| `REQ-EDG-019`, Security | `[MUST]` | `EdgeStreamSecurityIT.shouldRejectUnauthorizedOperationStream()` | `TASK-5.10`, `TASK-5.11` |
| `REQ-EDG-020` | `[MUST]` | `EdgeTransportIT.shouldRejectFinancialMutationOverZeroRtt()` | `TASK-5.3`, `TASK-5.4`, `TASK-5.5` |
| `REQ-EDG-021`, `I-DEDUP-001` | `[MUST]` | `NatsEdgeCommandPublisherTest.shouldPublishWithDeduplicationHeader()` | `TASK-7.1`, `TASK-7.2` |
| `REQ-EDG-022`, `I-EDGE-003` | `[MUST]` | `CoreCommandConsumerTest.shouldDispatchToUseCaseAndNotifyHub()` | `TASK-7.3`, `TASK-7.4`, `TASK-7.5` |
| `I-IDEMPOTENCY-001` | `[MUST]` | `IdempotencyIT.shouldRejectConflictingOperationId()` | `TASK-3.7`, `TASK-3.8` |

---

## 2. Implementation Tasks (TDD Sequence)

### Phase 1: Binary Framing, Envelope Size & Segment Storage [MUST]
- [x] `TASK-1.1` [RED]: Write failing unit tests in `BinaryRecordCodecTest` verifying 54B record header, `CommandType` mapping, sequence numbering, and Castagnoli CRC32C.
- [x] `TASK-1.2` [GREEN]: Implement `SegmentHeader` (32B), `CommandType`, and `BinaryRecordCodec` passing all codec tests.
- [x] `TASK-1.3` [GREEN]: Implement `SegmentedFileJournal` with fixed-size 64MB segments and an explicit deployment-compatible physical allocation strategy, avoiding sparse-file assumptions. Ensure segment metadata and allocated capacity are durably persisted before the segment becomes eligible for writes.
- [x] `TASK-1.4` [RED]: Add binary-format contract tests covering big-endian multi-byte integers, version constants, length semantics (`Length` = total frame length, `PayloadLen` = payload bytes), and CRC coverage (excluding magic and CRC field itself).
- [x] `TASK-1.5` [GREEN]: Finalize `BinaryRecordCodec` format contract, enforcing 64KB envelope limit before payload allocation and rejecting malformed/oversized frames.

### Phase 2: Group Commit & Durable Fsync Pipeline [MUST]
- [x] `TASK-2.1` [RED]: Write failing unit tests in `GroupCommitEngineTest` asserting no futures complete before `fsync` completes.
- [x] `TASK-2.2` [GREEN]: Implement `GroupCommitEngine` flushing on `batchSize >= 100` OR `elapsed >= 1ms`, establishing `FileChannel.force(false)` as the durability barrier before completing accepted journal futures.
- [x] `TASK-2.3` [GREEN]: Implement spool capacity watermarks with hysteresis ($<70\%$ Normal, $80-95\%$ Pressure, $\ge 95\%$ Saturated, $<85\%$ Recovery) (`I-EDGE-005`).

### Phase 3: Command Acceptance Service, Bulkhead & Ingress Resilience [MUST]
- [x] `TASK-3.1` [RED]: Write failing unit tests in `CommandAcceptanceServiceTest` asserting broker vs journal routing and 202 semantics.
- [x] `TASK-3.2` [GREEN]: Implement `CommandAcceptanceService` orchestrating primary broker publish vs degraded spooling.
- [x] `TASK-3.3` [GREEN]: Implement `PerimeterRateLimiter` lock-free atomic token bucket ($P99 < 10\mu s$) (`REQ-EDG-004`).
- [x] `TASK-3.4` [GREEN]: Implement `IngressBulkhead` capping inflight requests at configurable `edge.bulkhead.max-inflight` (default 2,048) returning `HTTP 429` (`I-EDGE-002`).
- [x] `TASK-3.5` [GREEN]: Configure `BrokerCircuitBreaker` (Resilience4j) tripping on latency $> 50\text{ms}$ or error rate $> 10\%$.
- [x] `TASK-3.6` [GREEN]: Implement `EdgeRequestValidator` rejecting envelopes $> 64\text{KB}$ with `HTTP 413` (`REQ-EDG-015`).
- [x] `TASK-3.7` [RED]: Write tests asserting that replaying the same `operationId` with the exact same payload is idempotent, while reusing `operationId` with a different command payload is rejected with `HTTP 409 Conflict`.
- [x] `TASK-3.8` [GREEN]: Implement deterministic operationId/payload fingerprint validation using canonical command serialization (`SHA-256(canonicalPayload)`), rejecting conflicting payloads with `HTTP 409 Conflict`.

### Phase 4: Crash Recovery Worker, Fair Drain & Readiness Gate [MUST]
- [x] `TASK-4.1` [RED/GREEN]: Implement `JournalRecoveryWorker` with weighted fair scheduling reserving at least 20% of dispatch capacity for live traffic (`edge.recovery.live-reserved-share=0.20`) while allowing unused capacity to be consumed by replay (`REQ-EDG-014`).
- [x] `TASK-4.2` [RED/GREEN]: Implement storage corruption detection halting segment scan, preserving and isolating the affected segment (`.corrupt`), and raising a CRITICAL alert without DLQ routing (`REQ-EDG-018`).
- [x] `TASK-4.3` [GREEN]: Implement `EdgeReadinessHealthIndicator` (Spring Boot `ReactiveHealthIndicator`) emitting `OUT_OF_SERVICE` during recovery scan, switching to `UP` once operational (`I-EDGE-004`).
- [x] `TASK-4.4` [GREEN]: Implement `SpoolAckTracker` confirming JetStream PUBACK before reclaiming segment space (`REQ-EDG-016`).
- [x] `TASK-4.5` [RED]: Write recovery tests asserting that acknowledged records remain distinguishable from unacknowledged records after process restart.
- [x] `TASK-4.6` [GREEN]: Implement durable journal acknowledgement/checkpoint metadata allowing safe replay after crash, enforcing that a segment MUST NOT be reclaimed until all records contained in the segment have durable JetStream publish confirmation.

### Phase 5: Frontend Push Streaming & Multi-Protocol Transport [MUST]
- [x] `TASK-5.1` [RED]: Write failing integration test in `EdgeStreamIT` testing SSE push (`GET /operations/{opId}/stream`) emitting terminal events.
- [x] `TASK-5.2` [GREEN]: Implement `EdgeOperationsStreamController` providing Server-Sent Events stream using Reactor `Flux<ServerSentEvent<OperationStatusResponse>>` (`REQ-EDG-019`).
- [x] `TASK-5.3` [GREEN]: Implement `AltSvcWebFilter` injecting `Alt-Svc: h3=":8443"; ma=86400` header for transparent HTTP/3 (QUIC) upgrade (`REQ-EDG-020`).
- [x] `TASK-5.4` [RED]: Write failing integration tests in `EdgeTransportIT` asserting that financial mutation endpoints do not process HTTP/3 0-RTT early data and require 1-RTT before command acceptance.
- [x] `TASK-5.5` [GREEN]: Configure HTTP/3/QUIC transport policy so 0-RTT may establish transport connectivity but financial mutations are rejected/deferred until 1-RTT handshake is established.
- [x] `TASK-5.6` [RED]: Write integration tests in `EdgeStreamIT` asserting that an SSE subscriber receives current durable operation status when connecting after terminal transition (`I-EDGE-007`).
- [x] `TASK-5.7` [GREEN]: Implement SSE bootstrap from durable operation-status source (`DurableOperationStateProvider` SPI) before attaching subscriber to live `OperationStatusHub`.
- [x] `TASK-5.8` [RED]: Write a multi-instance integration test (`EdgeStreamClusterIT`) asserting that an operation status generated on one edge instance is delivered to an SSE subscriber connected to another edge instance.
- [x] `TASK-5.9` [GREEN]: Implement NATS-backed status event fan-out from the shared operation-status topic to each edge-local `OperationStatusHub`.
- [x] `TASK-5.10` [RED]: Write integration tests in `EdgeStreamSecurityIT` asserting that an authenticated client can stream only operations belonging to its authorized principal/tenant.
- [x] `TASK-5.11` [GREEN]: Implement operation-status authorization filter before establishing SSE subscriptions.

### Phase 6: Chaos Tests, Failure Gates & Benchmarks [MUST/SHOULD]
- [x] `TASK-6.1` [TEST]: Author `EdgeIngressIT` simulating broker outages, journal spillover, and node restart replay.
- [x] `TASK-6.2` [TEST]: Author `EdgeCapacityIT` asserting `HTTP 503` under spool saturation ($\ge 95\%$).
- [x] `TASK-6.3` [TEST]: Author `EdgeCorruptionIT` asserting segment isolation on CRC mismatch without DLQ pollution.
- [x] `TASK-6.4` [SHOULD]: Implement 14 Micrometer metrics in `EdgeObservabilityConfig` exporting to OTel / OpenObserve.
- [x] `TASK-6.5` [SHOULD]: Benchmark degraded journal throughput and durability latency targeting $\ge 20,000$ ops/sec and measuring P50/P95/P99 group-commit latency independently from broker-path latency.
- [x] `TASK-6.6` [MUST]: Verify that journal persistence does not store plaintext sensitive financial payloads and is compatible with SPEC-000.10 envelope encryption requirements.

### Phase 7: Edge-to-Core Bridge & Command Consumer Pipeline [MUST]
- [x] `TASK-7.1` [RED]: Write failing unit tests in `NatsEdgeCommandPublisherTest` asserting `CommandEnvelope` serialization, `Nats-Msg-Id: <operationId>` header injection, subject mapping to `commands.wallet.<type>`, and asynchronous completion on JetStream `PublishAck` (`REQ-EDG-021`).
- [x] `TASK-7.2` [GREEN]: Implement `NatsEdgeCommandPublisher` in `br.com.wallet.infrastructure.messaging.publisher` implementing `EdgeCommandPublisher` and registering as primary Spring bean (`REQ-EDG-021`).
- [x] `TASK-7.3` [RED]: Write failing unit tests in `CoreCommandConsumerTest` asserting:
  1. `shouldAckBusinessRejection()`: business rejections (`InsufficientFundsException`, `AccountBlockedException`) transition status to `FAILED`, notify hub, and commit `msg.ack()`.
  2. `shouldRetryTransientFailure()`: transient infrastructure errors (deadlock, pool timeout) trigger `msg.nakWithDelay()`.
  3. `shouldRoutePoisonMessageToDlq()`: publish to DLQ (`commands.dlq.*`), await confirmed durable JetStream `PubAck`, and ONLY THEN commit `msg.ack()` on original message.
- [x] `TASK-7.4` [GREEN]: Implement `CoreCommandConsumer` in `br.com.wallet.infrastructure.messaging.consumer` subscribing to `commands.wallet.*` on stream `commands` (`REQ-EDG-022`).
- [x] `TASK-7.5` [TEST]: Author end-to-end integration test (`EdgeToCoreIntegrationTest`) asserting full cycle: HTTP POST `/operations/transfers` $\to$ `202 ACCEPTED` $\to$ JetStream $\to$ `CoreCommandConsumer` $\to$ Ledger balances updated $\to$ SSE stream receives `COMPLETED`.
