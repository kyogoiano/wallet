# 📝 Task Breakdown: TASKS-000.02 — Transactional Outbox Pattern & Event Streaming

- **Associated Spec**: [`../SPEC-000.02-transactional-outbox-and-event-streaming.md`](file:///.spec/SPEC-000.02-transactional-outbox-and-event-streaming.md)
- **Associated Plan**: [`../plans/PLAN-000.02-transactional-outbox-and-event-streaming.md`](file:///.spec/plans/PLAN-000.02-transactional-outbox-and-event-streaming.md)
- **Status**: 🟢 **Completed & Verified**

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-OUT-001`, `I-OUTBOX-001` | `OutboxIT.shouldProcessOutboxEvents()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-OUT-002`, `I-CONCURRENCY-002` | `OutboxRelayTest.shouldClaimBatchAndDelegateToEventProcessor()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-OUT-003`, `I-RETRY-001` | `OutboxEventProcessorTest.shouldMarkFailedWithBackoffOnPublisherException()` | `TASK-3.1` |
| `REQ-OUT-004`, `I-RETRY-002` | `OutboxEventProcessorTest.shouldMarkAsDeadWhenRetriesExhausted()` | `TASK-3.2` |
| `REQ-OUT-005` | `OutboxIT.shouldProcessOutboxEvents()` | `TASK-2.3` |
| `REQ-OUT-006` | `OutboxIT.shouldNotProcessBeforeRetryTime()` | `TASK-3.3` |
| `REQ-OUT-007`, `I-DEDUP-001` | `OutboxIT.shouldNotDuplicateOutboxEventsForSameOperation()` | `TASK-4.1` |
| `REQ-OUT-008` | `OutboxIT.shouldHandleInvalidPayloadGracefully()` | `TASK-3.4` |

---

## 2. Implementation Tasks

### Phase 1: Outbox Persistence & Storage [MUST]
- [x] `TASK-1.1`: Define `outbox` table schema and indices in PostgreSQL migration script (`schema.sql`).
- [x] `TASK-1.2`: Implement `OutboxDao.save(...)` inserting domain events within the caller's transaction boundary.

### Phase 2: Non-Blocking Claiming & Relay Engine [MUST]
- [x] `TASK-2.1`: Implement `OutboxDao.claimBatch(...)` using PostgreSQL CTE with `FOR UPDATE SKIP LOCKED`.
- [x] `TASK-2.2`: Implement `OutboxRelay.process()` scheduled runner executing every 10 seconds.
- [x] `TASK-2.3`: Implement `OutboxDao.markAsProcessed(...)` setting status to `PROCESSED` with current timestamp.

### Phase 3: Retry Backoff, Fault Handling & Poison Message Safety [MUST]
- [x] `TASK-3.1`: Implement exponential backoff formula ($2^{\text{retry\_count} + 1}\text{s}$) in `OutboxEventProcessor`.
- [x] `TASK-3.2`: Implement exhaustion gate moving events to `DEAD` when `retry_count > 10` (`OutboxDao.markAsDead`).
- [x] `TASK-3.3`: Enforce `next_retry_at <= now` filter in `claimBatch` query preventing premature re-execution.
- [x] `TASK-3.4`: Implement defensive JSON schema pre-validation in `OutboxEventProcessor` to catch corrupt payloads.

### Phase 4: NATS JetStream Publishing & Deduplication [MUST]
- [x] `TASK-4.1`: Implement `NatsEventPublisher` with `Nats-Msg-Id` header for server-side deduplication.
- [x] `TASK-4.2`: Integration test complete lifecycle using Testcontainers PostgreSQL and NATS JetStream (`OutboxIT`).
