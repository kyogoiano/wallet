# 📝 Task Breakdown: TASKS-000.01 — Core Transactional Ledger & Double-Entry Engine

- **Associated Spec**: [`../SPEC-000.01-core-transactional-ledger-and-double-entry.md`](file:///.spec/SPEC-000.01-core-transactional-ledger-and-double-entry.md)
- **Associated Plan**: [`../plans/PLAN-000.01-core-transactional-ledger-and-double-entry.md`](file:///.spec/plans/PLAN-000.01-core-transactional-ledger-and-double-entry.md)
- **Status**: 🟢 **Completed & Verified**

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-LED-001`, `I-ATOMICITY-001` | `TransferFundsIT.shouldTransferFundsSuccessfully()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-LED-002`, `I-BALANCE-002` | `WithdrawFundsServiceTest.shouldThrowWhenInsufficientFunds()` | `TASK-1.3` |
| `REQ-LED-003`, `REQ-LED-004` | `TransferFundsServiceTest.shouldRejectNegativeAmount()` | `TASK-1.4` |
| `REQ-LED-005`, `I-IDEMPOTENCY-001` | `TransferFundsIT.shouldHandleIdempotentRequest()` | `TASK-1.5` |
| `REQ-LED-006`, `I-BALANCE-001` | `ReplayWalletIT.shouldReplayWalletBalanceCorrectly()` | `TASK-2.1` |
| `REQ-LED-007`, `I-LEDGER-002` | `ValidateLedgerIT.shouldValidateUnmodifiedLedger()` | `TASK-2.2` |
| `REQ-LED-008` | `BalanceIT.shouldRetrieveHistoricalBalance()` | `TASK-2.3` |
| `I-CONCURRENCY-001` | `TransferFundsIT.shouldExecuteConcurrentTransfersWithoutDeadlock()` | `TASK-3.1` |

---

## 2. Implementation Tasks

### Phase 1: Core Double-Entry Use Cases [MUST]
- [x] `TASK-1.1`: Implement `TransferFundsService` with lexicographical UUID lock ordering (`I-CONCURRENCY-001`).
- [x] `TASK-1.2`: Implement `DepositFundsService` with positive amount validations and atomic projection mutation.
- [x] `TASK-1.3`: Implement `WithdrawFundsService` verifying balance sufficiency before applying debit.
- [x] `TASK-1.4`: Implement domain validations in `Validations.validatePositiveAmount(...)`.
- [x] `TASK-1.5`: Implement operation idempotency check via `WalletOperationsDao.startOperation(...)`.

### Phase 2: Ledger Audit, Replay & Historical Balance [MUST]
- [x] `TASK-2.1`: Implement `ReplayWalletService` re-aggregating credits and debits directly from ledger entries.
- [x] `TASK-2.2`: Implement `ValidateLedgerService` with hash mismatch detection and chain break checking.
- [x] `TASK-2.3`: Implement `BalanceService.getHistoricalBalance(...)` point-in-time calculation.

### Phase 3: REST Exposure & Concurrency Verification
- [x] `TASK-3.1`: Implement `WalletController` and `OperationsController` REST endpoints.
- [x] `TASK-3.2`: Verify concurrent multi-threaded transfer executions with Testcontainers PostgreSQL (`TransferFundsIT`).
