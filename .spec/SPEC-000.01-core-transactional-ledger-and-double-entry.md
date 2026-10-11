# 📐 Specification: SPEC-000.01 — Core Transactional Ledger & Double-Entry Engine

- **Initiative**: Core Banking Domain (`br.com.wallet.ledger`)
- **Status**: 🟢 **Implemented & Verified (Reverse-Engineered)**
- **Baseline**: Wallet Service V1 Core Architecture
- **Associated Plan**: [`plans/PLAN-000.01-core-transactional-ledger-and-double-entry.md`](file:///.spec/plans/PLAN-000.01-core-transactional-ledger-and-double-entry.md)
- **Associated Tasks**: [`tasks/TASKS-000.01-core-transactional-ledger-and-double-entry.md`](file:///.spec/tasks/TASKS-000.01-core-transactional-ledger-and-double-entry.md)
- **Execution Summary**: [`summaries/SUMMARY-000.01-core-transactional-ledger-and-double-entry.md`](file:///.spec/summaries/SUMMARY-000.01-core-transactional-ledger-and-double-entry.md)

---

## 1. Intent & Business Value

### 1.1 Problem Statement
Financial software requires absolute accounting correctness, mathematical tamper-evidence, and strict concurrency safety. Mutable balances in traditional databases are vulnerable to race conditions, double-spending, and silent record manipulation.

### 1.2 Solution Intent
Establish an append-only, cryptographically hash-chained transactional ledger (`ledger`) as the immutable single source of truth, alongside a high-performance balance projection (`accounts`). Provide atomic double-entry money movement (`Transfer`, `Deposit`, `Withdraw`), deterministic deadlock prevention, and full ledger replay reconstruction.

---

## 2. Mathematical Invariants

- **`I-LEDGER-001` (Append-Only Immutability)**: Existing ledger entries must never be mutated or deleted. `UPDATE` and `DELETE` on `ledger` are strictly forbidden.
- **`I-LEDGER-002` (Deterministic Hash-Chaining)**:
  $$\text{hash}_n = \text{SHA256}(\text{hash}_{n-1} + \text{walletId} + \text{normalizedAmount} + \text{type} + \text{operationId} + \text{sequence})$$
  Genesis entry uses constant $\text{hash}_0 = \text{"GENESIS"}$. Trailing zeros are stripped from `BigDecimal` amounts.
- **`I-BALANCE-001` (Mathematical Projection Equality)**:
  $$\text{Balance}(\text{walletId}) = \sum \text{Credits} - \sum \text{Debits}$$
- **`I-BALANCE-002` (Non-Negative Balance Constraint)**:
  $$\forall t, \quad \text{Balance}(\text{walletId}, t) \ge 0.00$$
- **`I-CONCURRENCY-001` (Deterministic Row Lock Ordering)**:
  $$\text{lockOrder}(A, B) = \min(\text{UUID}_A, \text{UUID}_B) \to \max(\text{UUID}_A, \text{UUID}_B)$$
- **`I-IDEMPOTENCY-001` (Deterministic Replay Safety)**: Replay of an identical `operation_id` must return the cached result or throw `IdempotencyException` without creating duplicate ledger records.

---

## 3. MoSCoW Requirements

### 3.1 Product Intent (Observable Behavior)
- **`REQ-LED-001` [MUST]**: Multi-party fund transfers MUST debit the source wallet, credit the target wallet, and update balances atomically within a single transaction.
- **`REQ-LED-002` [MUST]**: Fund withdrawals that exceed current balance MUST be rejected immediately with `InsufficientFundsException` and cause zero ledger mutation.
- **`REQ-LED-003` [MUST]**: Transfers between the same wallet (`from == to`) MUST be rejected with `IllegalArgumentException`.
- **`REQ-LED-004` [MUST]**: Any operation with non-positive amount ($\le 0$) MUST be rejected with `IllegalArgumentException`.
- **`REQ-LED-005` [MUST]**: Operation replaying an already completed `operation_id` MUST throw `IdempotencyException` with zero ledger insertion.
- **`REQ-LED-006` [MUST]**: Replay reconstruction (`GET /wallets/{walletId}/replay`) MUST compute current balance by re-aggregating all historical ledger records.
- **`REQ-LED-007` [MUST]**: Ledger validation (`ValidateLedgerUseCase`) MUST detect corrupted hashes or broken links and report failure count.
- **`REQ-LED-008` [SHOULD]**: Historical balance query (`GET /wallets/{walletId}/balance/historical?at=...`) MUST compute exact balance as of specified timestamp.
- **`REQ-LED-009` [COULD]**: Paginated ledger inspection endpoint (`GET /wallets/{walletId}/ledger?limit=100`).
- **`REQ-LED-010` [WON'T]**: Overdraft credit lines or negative balance allowance.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Affected Component | Nature of Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| **Ledger Concurrency** | High parallel transfer contention on hot wallets | Lexicographical UUID lock ordering (`I-CONCURRENCY-001`) prevents circular wait deadlocks |
| **Account Lifecycle** | Operations involving non-active accounts must halt | `I-ACCOUNT-001` gate checks account status during lock acquisition (`SELECT FOR UPDATE`) |
| **Outbox Relay** | Domain events must be captured synchronously | Transactional outbox write executed in same DB transaction (`I-ATOMICITY-001`) |
| **Anti-Fraud Gate** | Pre-execution check must precede ledger locking | `FraudCheckHelper.performFraudCheck(...)` executes before database connection opens |

---

## 5. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-LED-001` (Transfer) | `TransferFundsIT.shouldTransferFundsSuccessfully()` | Negative amount $\to$ 400 Bad Request | Insufficient funds $\to$ `InsufficientFundsException` |
| `REQ-LED-002` (Withdraw) | `WithdrawFundsIT.shouldWithdrawFundsSuccessfully()` | Zero/Negative amount $\to$ 400 Bad Request | Exceed balance $\to$ `InsufficientFundsException` |
| `REQ-LED-005` (Idempotency)| `TransferFundsIT.shouldHandleIdempotentRequest()` | Null `operation_id` $\to$ 400 Bad Request | Replay completed ID $\to$ `IdempotencyException` |
| `REQ-LED-007` (Validation) | `ValidateLedgerIT.shouldValidateUnmodifiedLedger()` | N/A | Tampered hash $\to$ `isValid == false` |

---

## 6. Acceptance Criteria

- [x] All monetary math executes with scale 2 `BigDecimal` arithmetic.
- [x] Append-only ledger calculates SHA-256 hash chains deterministically (`HashUtilTest`).
- [x] Concurrent transfers between identical wallet pairs never deadlock (`TransferFundsIT`).
- [x] Replay balance reconstruction matches account projection balance 100% (`ReplayWalletIT`).
- [x] Unit and integration test suites pass green with $\ge 85\%$ core service coverage.
