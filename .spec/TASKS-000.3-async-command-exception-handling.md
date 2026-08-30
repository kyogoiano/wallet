# 📝 Task Breakdown: TASKS-000.3 — Asynchronous Command Exception Handling & Operation Status Tracking

- **Associated Spec**: [`SPEC-000.3-async-command-exception-handling.md`](file:///.spec/SPEC-000.3-async-command-exception-handling.md)
- **Associated Plan**: [`PLAN-000.3-async-command-exception-handling.md`](file:///.spec/PLAN-000.3-async-command-exception-handling.md)
- **Status**: Completed

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-OP-001`, `REQ-OP-002` | `WalletOperationsDaoTest.shouldFailOperation()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-OP-003`, `I-OP-STATE-001` | `AbstractCommandsConsumerTest.shouldRecordFailureOnBusinessException()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-OP-004` | `OperationQueryServiceTest.shouldReturnOperationStatus()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-OP-005`, `I-HTTP-202-001` | `OperationsControllerTest.shouldGetOperationStatus()`, `OperationsControllerTest.shouldReturn202OnCommands()` | `TASK-4.1`, `TASK-4.2` |
| `I-OP-STATE-002` | `OperationStatusTrackingIT.shouldTrackFailedOperationStatusWhenConsumerMarksFailed()` | `TASK-5.1` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Database Schema & DAO
- [x] `TASK-1.1` [RED]: Write unit test for `WalletOperationsDao.failOperation(...)` and `findOperation(...)`.
- [x] `TASK-1.2` [GREEN]: Update `schema.sql` (`src/main/resources/schema.sql`, `src/test/resources/schema.sql`, `docker/init/schema.sql`) with `updated_at`, `error_message`, `failure_type`.
- [x] `TASK-1.3` [GREEN]: Implement `failOperation` and `findOperation` in `WalletOperationsDao.java`.

### Phase 2: Consumer Exception Handling & State Transition
- [x] `TASK-2.1` [RED]: Write unit tests in `AbstractCommandsConsumerTest` verifying `failOperation` call on `BusinessException` and DLQ escalation.
- [x] `TASK-2.2` [GREEN]: Update `AbstractCommandsConsumer` to inject `OperationStateUseCase` and invoke `markOperationFailed` before ACKing/DLQing.

### Phase 3: Operation Query Use Case (Spring Modulith API)
- [x] `TASK-3.1` [RED]: Write unit test for `OperationStateService` implementing `OperationQueryUseCase`.
- [x] `TASK-3.2` [GREEN]: Create `OperationStatusResponse` record in `br.com.wallet.ledger.api.dto`, `OperationQueryUseCase` & `OperationStateUseCase` in `br.com.wallet.ledger.api`, and `OperationStateService` in `br.com.wallet.ledger.internal.service`.

### Phase 4: REST Controller & Status Endpoint
- [x] `TASK-4.1` [RED]: Write `OperationsControllerTest` for `GET /operations/{operationId}` (200 OK with status and 404 NOT FOUND).
- [x] `TASK-4.2` [GREEN]: Update `OperationsApi` and `OperationsController` with `GET /operations/{operationId}`, ensuring all command POST endpoints retain `202 ACCEPTED`.

### Phase 5: End-to-End Verification & Convergence
- [x] `TASK-5.1` [RED $\rightarrow$ GREEN]: Write integration test verifying that when an asynchronous transfer runs with insufficient funds, the consumer ACKs the message and the operation status is marked as `FAILED` with `error_message = "Insufficient funds"`.
- [x] `TASK-5.2` [VERIFY]: Verify Modulith boundaries, API contracts, and schema migrations.
- [x] `TASK-5.3` [SUMMARY]: Generate `.spec/summaries/SUMMARY-000.3-async-command-exception-handling.md`.
