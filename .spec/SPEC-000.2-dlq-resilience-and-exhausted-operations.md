# 📋 Specification: SPEC-000.2 — DLQ Resilience, EXHAUSTED Status & Spring Modulith Isolation

- **Status**: Ratified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-28
- **Target Release / Milestone**: Baseline Platform & Operational Resilience (`br.com.wallet.dlq`)

---

## 1. Intent & Business Value

In high-throughput financial distributed systems, transient failures (e.g. database deadlocks, connection pool starvation, temporary network partitions) must be automatically retried without blocking transaction pipelines. However, retrying indefinitely leads to infinite retry storms, CPU churn, and poison queues.

This specification elevates **Dead Letter Queue (DLQ) & Operational Recovery** to a first-class **Spring Modulith Capability Module** (`br.com.wallet.dlq`):
1. **Bounded Automatic Replay**: Limits automated JetStream replay attempts to a maximum of **3 retries**.
2. **`EXHAUSTED` State**: Once 3 attempts fail, operations transition to `EXHAUSTED` status, stopping automated replays and preventing retry amplification.
3. **Manual Operational Control API**: Exposes REST endpoints allowing human operators and automated runbooks to inspect payloads, manually re-trigger replay for exhausted operations, or discard unrecoverable poison records.
4. **Clean Modulith Architecture**: Decouples DLQ recovery logic from generic infrastructure adapters into a cohesive domain module.

---

## 2. Scope & Non-Goals

### In Scope
- **Spring Modulith Isolation**: Create `br.com.wallet.dlq` module with published API in `br.com.wallet.dlq.api` (`@NamedInterface("api")`).
- **New DLQ Lifecycle State**: Introduce `EXHAUSTED` and `DISCARDED` to `DlqStatus`.
- **Automatic Replay Cap**: Update `claimBatch` and `markFailed` to transition to `EXHAUSTED` when `retry_count >= 3`.
- **Operator REST API**:
  - `GET /dlq/operations`: Paginated search by `status` (e.g. `EXHAUSTED`), `failureType`, `eventType`.
  - `GET /dlq/operations/{id}`: Detailed inspection of payload, headers, and error logs.
  - `POST /dlq/operations/{id}/replay`: Manual replay trigger for `EXHAUSTED` / `FAILED` operations.
  - `POST /dlq/operations/{id}/discard`: Manual dismissal marking the operation as `DISCARDED`.
  - `POST /dlq/operations/replay-exhausted`: Batch replay for all currently exhausted operations.
- **Database Migration**: Ensure PostgreSQL `dlq_operations` schema and check constraints support new statuses.

### Non-Goals
- Modifying core ledger balance calculation or immutable cryptographic hashing (`I-LEDGER-001`).
- Direct modification of financial transaction business rules.

---

## 3. Mathematical & System Invariants

- **`I-DLQ-001` (Bounded Automatic Replay)**: The automatic replay scheduler must NEVER execute more than 3 automated retries for any DLQ record:
  $$\text{AutomaticReplayAttempts}(event) \le 3$$
  When $\text{retry\_count} \ge 3$, status MUST become `EXHAUSTED` and automated claiming MUST ignore it.
- **`I-DLQ-002` (State Machine Exclusivity)**: A DLQ operation must strictly follow allowable state transitions:
  $$\text{PENDING} \rightarrow \text{PROCESSING} \rightarrow \begin{cases} \text{COMPLETED} \\ \text{FAILED} \ (\text{retry} < 3) \\ \text{EXHAUSTED} \ (\text{retry} \ge 3) \end{cases}$$
  $$\text{EXHAUSTED} \xrightarrow{\text{manual operator}} \begin{cases} \text{PENDING} \ (\text{Replay}) \\ \text{DISCARDED} \ (\text{Dismiss}) \end{cases}$$
- **`I-DLQ-003` (Replay Traceability)**: Every manually or automatically replayed message MUST maintain the original `operation_id`, original `userId`, propagate `replayed=true`, and increment `replay_count`.
- **`I-DLQ-004` (Modulith Boundary Encapsulation)**: The `dlq` module must have zero outgoing dependencies on banking use cases (`ledger` or `savings` or `goals`), depending only on `:core` and publishing to NATS.

---

## 4. Functional Requirements

- **`REQ-DLQ-001`**: Move DLQ persistence, replay engine, and status definitions from `infrastructure` to `br.com.wallet.dlq`.
- **`REQ-DLQ-002`**: Support statuses: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `EXHAUSTED`, `DISCARDED`.
- **`REQ-DLQ-003`**: In `claimBatch()`, only claim records where `status IN ('PENDING', 'FAILED') AND retry_count < 3 AND (next_retry_at IS NULL OR next_retry_at <= now)`.
- **`REQ-DLQ-004`**: In `markFailed()`, if the incremented `retry_count >= 3`, set status to `EXHAUSTED` and set `next_retry_at = NULL`.
- **`REQ-DLQ-005`**: Implement `DlqManagementUseCase.replayOperation(UUID id)` allowing manual replay: resets status to `PENDING` (or immediately publishes to NATS) and logs operator action.
- **`REQ-DLQ-006`**: Implement `DlqManagementUseCase.discardOperation(UUID id, String reason)` setting status to `DISCARDED`.
- **`REQ-DLQ-007`**: Implement `DlqQueryUseCase.findOperations(DlqQueryFilter filter, Pageable pageable)` returning paginated DLQ records.
- **`REQ-DLQ-008`**: Implement REST controller `/dlq/operations` exposing query, manual replay, batch replay, and discard actions with OpenAPI tags.

---

## 5. Non-Functional Requirements

- **Performance**: Batch claiming using PostgreSQL `FOR UPDATE SKIP LOCKED` must execute in $< 5\text{ms}$.
- **Resilience**: Zero infinite loop between DLQ and command consumers.
- **Observability**: OpenTelemetry spans (`dlq.manual_replay`, `dlq.auto_replay`, `dlq.discard`) with baggage propagation.

---

## 6. Interface Contracts

### HTTP / REST API

```http
# 1. Query DLQ Operations
GET /dlq/operations?status=EXHAUSTED&limit=20&offset=0
Response 200 OK:
[
  {
    "id": "c1f7253a-...",
    "operationId": "2d0b175d-...",
    "userId": "2d0b175d-...",
    "subject": "commands.deposit",
    "eventType": "Deposit",
    "status": "EXHAUSTED",
    "error": "TransientException",
    "errorMessage": "DB Lock timeout",
    "retryCount": 3,
    "createdAt": "2026-08-28T12:00:00Z",
    "failureType": "TRANSIENT"
  }
]

# 2. Get Single DLQ Operation Detail
GET /dlq/operations/{id}
Response 200 OK:
{
  "id": "c1f7253a-...",
  "operationId": "2d0b175d-...",
  "payload": "{\"walletId\":\"...\",\"amount\":100.00}",
  "status": "EXHAUSTED",
  ...
}

# 3. Manually Replay Exhausted Operation
POST /dlq/operations/{id}/replay
Response 200 OK:
{
  "id": "c1f7253a-...",
  "status": "PENDING",
  "message": "Operation queued for immediate replay"
}

# 4. Manually Discard Operation
POST /dlq/operations/{id}/discard
Content-Type: application/json
{
  "reason": "Unrecoverable malformed request confirmed by support"
}
Response 200 OK:
{
  "id": "c1f7253a-...",
  "status": "DISCARDED"
}

# 5. Batch Replay All Exhausted Operations
POST /dlq/operations/replay-exhausted
Response 200 OK:
{
  "replayedCount": 5
}
```

---

## 7. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant Enforced |
| :--- | :--- | :--- |
| Automatic retry reaches 3 attempts | Transition to `EXHAUSTED`, stop automatic scheduling | `I-DLQ-001`, `I-DLQ-002` |
| Manual replay on already COMPLETED record | Reject with 400 Bad Request ("Operation already completed") | `I-DLQ-002` |
| Manual replay on EXHAUSTED record | Transition back to `PENDING` or publish immediately to JetStream | `I-DLQ-002`, `I-DLQ-003` |
| Replayed message encounters idempotency | Consumer immediately ACKs without erroring back to DLQ | `I-IDEMPOTENCY-001` |

---

## 8. Acceptance Criteria

- [x] `br.com.wallet.dlq` created as independent Spring Modulith module.
- [x] `DlqStatus` enum contains `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `EXHAUSTED`, `DISCARDED`.
- [x] Automatic `DlqReplayEngine` halts after 3 attempts and marks record as `EXHAUSTED`.
- [x] REST API endpoints `/dlq/operations` tested and documented with OpenAPI.
- [x] Modulith architecture verification passes (`ModulithArchitectureTest`).
- [x] 100% unit and integration tests passing.

