# 📋 Specification: SPEC-000.3 — Asynchronous Command Exception Handling & Operation Status Tracking

- **Status**: Ratified
- **Author**: Antigravity Agent & Financial Core Team
- **Date**: 2026-08-29
- **Target Release / Milestone**: Phase 0.3 / Baseline Resilience

---

## 1. Intent & Business Value

In the current asynchronous command architecture, incoming financial operations (`/operations/transfer`, `/operations/deposit`, `/operations/withdraw`, `/wallets/deposit`) are published to NATS JetStream command topics and immediately return `202 ACCEPTED` to the client.

When background consumers (`TransferCommandConsumer`, `WithdrawCommandConsumer`, `DepositCommandConsumer`, `CreateWalletCommandConsumer`) process these commands, domain business exceptions (e.g. `InsufficientFundsException`, `AccountBlockedException`, `AccountNotFoundException`, `UserNotAllowedException`, `IllegalArgumentException`) are thrown during use case execution. While `TracingAspect` catches and logs these exceptions on the OpenTelemetry span, the consumer currently swallows them into an `ACK` without recording the failure reason or updating the operation status. As a result, operations remain trapped in `status = 'PROCESSING'` indefinitely in PostgreSQL and callers have no mechanism to observe failure reasons.

This specification establishes deterministic asynchronous exception handling, operation status transitions to `FAILED` with error metadata, and an operation status query API while preserving `202 ACCEPTED` response contracts for command ingestion.

---

## 2. Scope & Non-Goals

### In Scope
- **Operation Failure Persistence**: Persisting `FAILED` status, `error_message`, and `failure_type` (`BUSINESS`, `TRANSIENT`, `POISON`) in `wallet_operations` table.
- **Consumer Failure Hook**: Ensuring `AbstractCommandsConsumer` transitions `wallet_operations` to `FAILED` before ACKing non-retriable business failures or forwarding exhausted retries to DLQ.
- **Operation Status Query API**: Exposing `GET /operations/{operationId}` endpoint to retrieve operation status and failure diagnostics.
- **Modulith Boundary Compliance**: Providing `OperationQueryUseCase` in `br.com.wallet.ledger.api` adhering to Spring Modulith boundaries (`I-MODULITH-001`, `I-MODULITH-002`).
- **HTTP 202 Contract Preservation**: Ensuring all asynchronous command ingestion endpoints continue returning `202 ACCEPTED`.

### Non-Goals
- Changing the asynchronous ingestion model to synchronous REST.
- Altering the cryptographic hash-chaining logic in `ledger` or the $O(1)$ anti-fraud evaluation path.
- Modifying NATS JetStream stream definitions or subject routing topologies.

---

## 3. Mathematical & System Invariants

- **`I-OP-STATE-001` (Deterministic Operation Lifecycle)**: Every operation registered in `wallet_operations` must reach a terminal state:
  $$\text{State}(\text{operationId}) \in \{\text{COMPLETED}, \text{FAILED}\}$$
  An operation must never remain in `PROCESSING` after consumer completion or non-retriable failure.
- **`I-OP-STATE-002` (Failure Isolation)**: Persisting failure status in `wallet_operations` must execute outside the rolled-back domain transaction to guarantee persistence even when domain balance mutations roll back.
- **`I-OP-IDEMPOTENCY-001` (Idempotent Status Invariance)**: Querying `GET /operations/{operationId}` must return identical status and error details on repeated invocations.
- **`I-HTTP-202-001` (Asynchronous Acceptance)**: Command endpoints accepting valid requests for background processing must strictly return `202 ACCEPTED` with no synchronous blocking on worker threads.

---

## 4. Functional Requirements

- **`REQ-OP-001` (Database Schema Enrichment)**: The `wallet_operations` table must include `updated_at`, `error_message`, and `failure_type` columns.
- **`REQ-OP-002` (DAO Failure Transition)**: `WalletOperationsDao` must provide a `failOperation(UUID operationId, String errorMessage, String failureType)` method that transitions status from `PROCESSING` to `FAILED`.
- **`REQ-OP-003` (Consumer Exception Handling)**: `AbstractCommandsConsumer` must invoke `failOperation` whenever an unhandled `BusinessException`, `PermanentException`, or DLQ exhaustion occurs.
- **`REQ-OP-004` (Operation Query Use Case)**: Introduce `OperationQueryUseCase` in `br.com.wallet.ledger.api` returning `Optional<OperationStatusResponse>`.
- **`REQ-OP-005` (REST Status Endpoint)**: `OperationsController` must expose `GET /operations/{operationId}` returning `200 OK` with status details or `404 NOT_FOUND`.

---

## 5. Non-Functional Requirements

- **Performance**: Operation status queries must resolve in $< 5\text{ms}$ via primary key lookup on `wallet_operations.operation_id`.
- **Consistency**: Status transitions must be immediate and atomic in PostgreSQL.
- **Observability**: `TracingAspect` and consumer logs must record operation failure tags (`status = FAILED`, `error.message`, `failure.type`).

---

## 6. Interface Contracts

### HTTP Status Query API
```http
GET /operations/{operationId}
```

#### Response (200 OK — Completed):
```json
{
  "operationId": "0a35fb14-75ee-4125-943b-500893c30d33",
  "status": "COMPLETED",
  "errorMessage": null,
  "failureType": null,
  "createdAt": "2026-08-29T18:30:00Z",
  "updatedAt": "2026-08-29T18:30:01Z"
}
```

#### Response (200 OK — Failed):
```json
{
  "operationId": "0a35fb14-75ee-4125-943b-500893c30d33",
  "status": "FAILED",
  "errorMessage": "Insufficient funds",
  "failureType": "BUSINESS",
  "createdAt": "2026-08-29T18:30:00Z",
  "updatedAt": "2026-08-29T18:30:01Z"
}
```

#### Response (404 NOT FOUND):
```json
{
  "code": "NOT_FOUND",
  "message": "Operation not found"
}
```

---

## 7. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant Enforced |
| :--- | :--- | :--- |
| Insufficient Funds during Consumer processing | Record `FAILED` (`error_message = "Insufficient funds"`), ACK message | `I-OP-STATE-001`, `I-OP-STATE-002` |
| Account Blocked during Consumer processing | Record `FAILED` (`error_message = "Account is blocked"`), ACK message | `I-ACCOUNT-001`, `I-OP-STATE-001` |
| Poison payload / Deserialization error | Record DLQ event, ACK message | `I-OUTBOX-001` |
| Querying non-existent `operationId` | Return HTTP 404 NOT FOUND | `I-OP-IDEMPOTENCY-001` |

---

## 8. Acceptance Criteria

- [ ] `wallet_operations` table altered with `error_message`, `failure_type`, and `updated_at`.
- [ ] `WalletOperationsDao.failOperation(...)` updates status to `FAILED`.
- [ ] `AbstractCommandsConsumer` updates operation state to `FAILED` upon non-retriable exceptions.
- [ ] `GET /operations/{operationId}` returns correct operation status and failure diagnostics.
- [ ] Integration tests verify end-to-end failure recording when transferring with insufficient funds.
- [ ] All command endpoints continue returning `202 ACCEPTED`.
