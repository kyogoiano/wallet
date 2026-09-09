# 📐 Specification: SPEC-000.02 — Transactional Outbox Pattern & Event Streaming

- **Initiative**: Reliable Asynchronous Messaging (`br.com.wallet.ledger.internal.outbox`, `br.com.wallet.infrastructure.messaging`)
- **Status**: 🟢 **Implemented & Verified (Reverse-Engineered)**
- **Baseline**: Wallet Service V1 Outbox & NATS JetStream Infrastructure
- **Associated Plan**: [`plans/PLAN-000.02-transactional-outbox-and-event-streaming.md`](file:///.spec/plans/PLAN-000.02-transactional-outbox-and-event-streaming.md)
- **Associated Tasks**: [`tasks/TASKS-000.02-transactional-outbox-and-event-streaming.md`](file:///.spec/tasks/TASKS-000.02-transactional-outbox-and-event-streaming.md)
- **Execution Summary**: [`summaries/SUMMARY-000.02-transactional-outbox-and-event-streaming.md`](file:///.spec/summaries/SUMMARY-000.02-transactional-outbox-and-event-streaming.md)

---

## 1. Intent & Business Value

### 1.1 Problem Statement
In distributed financial architectures, direct network publishing to message brokers during transaction commits suffers from the classic dual-write problem: if the broker is unavailable or times out, the local transaction cannot know if the event was received, risking silent data loss or inconsistent distributed state across services.

### 1.2 Solution Intent
Establish a resilient **Transactional Outbox Pattern** guaranteeing at-least-once asynchronous event delivery. Persist domain events (`outbox`) atomically within the ledger write transaction, decouple downstream messaging through scheduled batch claiming with `SKIP LOCKED`, and publish to NATS JetStream with deterministic exponential retry backoff and message deduplication.

---

## 2. Mathematical Invariants

- **`I-OUTBOX-001` (Atomic Outbox Invariant)**: Domain event persistence MUST occur inside the exact same database transaction boundary as ledger writes:
  $$\text{Tx}_{\text{atomic}} = \{ \text{Update}(\text{accounts}), \, \text{Insert}(\text{ledger}), \, \text{Insert}(\text{outbox}) \}$$
- **`I-CONCURRENCY-002` (Non-Blocking Claiming)**: Outbox polling queries MUST claim batches using `FOR UPDATE SKIP LOCKED` to ensure parallel relay workers never deadlock or block active transactions:
  $$\text{Claimed} = \text{SelectBatch}(\text{now}, \text{limit}) \cap \neg \text{LockedRecords}$$
- **`I-RETRY-001` (Exponential Backoff Schedule)**: Failed events calculate next execution timestamp deterministically:
  $$\Delta t_{\text{retry}} = 2^{\text{retry\_count} + 1} \text{ seconds}, \quad \text{next\_retry\_at} = \text{now} + \Delta t_{\text{retry}}$$
- **`I-RETRY-002` (Exhaustion & Dead-Letter Gate)**: When $\text{retry\_count} > 10$, automatic retry attempts terminate and status transitions permanently to `DEAD`.
- **`I-DEDUP-001` (Deterministic JetStream Deduplication)**: Every published message MUST include standard header:
  $$\text{Nats-Msg-Id} = \text{eventType} + \text{"-"} + \text{aggregateId}$$

---

## 3. MoSCoW Requirements

### 3.1 Product Intent (Observable Behavior)
- **`REQ-OUT-001` [MUST]**: Completed domain operations (`Transfer`, `Deposit`, `Withdraw`, `CreateWallet`) MUST persist an outbox event atomically.
- **`REQ-OUT-002` [MUST]**: `OutboxRelay` MUST poll every 10 seconds, atomically claiming up to 100 eligible records into `PROCESSING` state.
- **`REQ-OUT-003` [MUST]**: If publishing fails, event MUST transition to `FAILED`, increment `retry_count`, and schedule next retry via exponential backoff.
- **`REQ-OUT-004` [MUST]**: Events failing more than 10 times MUST transition to `DEAD` without crashing the scheduled relay runner.
- **`REQ-OUT-005` [MUST]**: Successfully published events MUST transition to `PROCESSED` with non-null `processed_at` timestamp.
- **`REQ-OUT-006` [MUST]**: Events MUST NOT be evaluated before their scheduled `next_retry_at` timestamp.
- **`REQ-OUT-007` [MUST]**: Replaying identical `operation_id` MUST NOT create duplicate outbox entries.
- **`REQ-OUT-008` [SHOULD]**: Payload validation failure MUST transition event to `FAILED` with retry schedule rather than dropping the record.
- **`REQ-OUT-009` [COULD]**: Outbox archiving worker cleaning records in `PROCESSED` status older than 30 days.
- **`REQ-OUT-010` [WON'T]**: Direct synchronous HTTP or broker publishing on the core transaction path.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Affected Component | Nature of Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| **Ledger Write Path** | Adds one `INSERT INTO outbox` to transaction | Negligible overhead ($< 2\text{ms}$); ensures zero dual-write anomalies (`I-OUTBOX-001`) |
| **Database Locks** | Outbox polling scans `outbox` table periodically | `FOR UPDATE SKIP LOCKED` avoids lock contention with concurrent inserts |
| **NATS JetStream** | Network blips or broker restarts cause transient errors | Exponential backoff prevents broker hammering; `Nats-Msg-Id` guarantees deduplication |
| **DLQ Capability** | Exhausted outbox events become permanent failures | `DEAD` records tracked and made accessible for operator analysis |

---

## 5. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-OUT-001` / `005` | `OutboxIT.shouldProcessOutboxEvents()` | Invalid JSON $\to$ `shouldHandleInvalidPayloadGracefully()` | Uncommitted DB tx $\to$ 0 outbox entries |
| `REQ-OUT-003` / `006` | `OutboxIT.shouldRetryProcessingLater()` | NATS down $\to$ status `FAILED` + retry backoff | Early polling $\to$ `shouldNotProcessBeforeRetryTime()` |
| `REQ-OUT-004` (Max Retry) | `OutboxIT.shouldStopRetryingAfterMaxAttempts()` | N/A | Exceeds 10 retries $\to$ status `DEAD` |
| `REQ-OUT-007` (Dedup) | `OutboxIT.shouldNotDuplicateOutboxEventsForSameOperation()` | Duplicate `operation_id` $\to$ `IdempotencyException` | Replayed request $\to$ exactly 1 outbox record |

---

## 6. Acceptance Criteria

- [x] All outbox events written inside transaction boundary (`OutboxDao.save`).
- [x] Batch claim uses PostgreSQL CTE with `SKIP LOCKED` (`OutboxDao.claimBatch`).
- [x] Relay schedules retries via $2^{\text{retry\_count}}$ exponential backoff (`OutboxEventProcessorTest`).
- [x] Outbox integration suite passes green with Testcontainers NATS & PostgreSQL (`OutboxIT`).
