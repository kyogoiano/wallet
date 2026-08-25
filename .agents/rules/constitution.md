---
name: constitution
description: Non-negotiable financial invariants, architectural constraints, and safety principles for Wallet Service.
trigger: always_on
---

# 🏛️ Wallet Service Constitution

These core invariants and architectural principles are **non-negotiable** and must never be violated by any agent, feature, or refactor.

---

## 1. Absolute Financial Invariants

- **`I-LEDGER-001` (Immutable Source of Truth)**: The ledger table is append-only and cryptographically tamper-evident. Existing ledger entries must never be modified or deleted (`UPDATE`/`DELETE` on `ledger` is forbidden).
- **`I-LEDGER-002` (Hash-Chaining Integrity)**: Every ledger entry must calculate its hash deterministically from the previous hash, wallet ID, normalized amount, operation type, operation ID, and sequence number:
  $$\text{hash}_n = \text{SHA256}(\text{hash}_{n-1} + \text{walletId} + \text{amount} + \text{type} + \text{operationId} + \text{sequence})$$
- **`I-BALANCE-001` (Strong Mathematical Consistency)**: The balance stored in `accounts` is a projection. It must always equal the aggregate sum of all credit and debit ledger entries for that wallet:
  $$\text{Balance}(\text{walletId}) = \sum \text{Credits} - \sum \text{Debits}$$
- **`I-BALANCE-002` (Non-Negative Balances)**: An account balance can never drop below zero unless overdraft protection is explicitly defined. Withdrawals and transfer debits must be rejected if funds are insufficient.
- **`I-ACCOUNT-001` (Account Lifecycle Gate)**: Any monetary operation (`Transfer`, `Deposit`, `Withdraw`, `Savings Sweep`) involving a participating account whose status is not `ACTIVE` (e.g. `BLOCKED`, `SUSPENDED`, `FROZEN`) MUST be rejected immediately with `AccountBlockedException`.
- **`I-ATOMICITY-001` (Single Transaction Boundary)**: All mutations involving account balances, ledger entries, and outbox events must execute inside a single atomic database transaction (`SELECT FOR UPDATE` on participating accounts).

---

## 2. Idempotency & Concurrency Invariants

- **`I-IDEMPOTENCY-001` (Deterministic Operations)**: Every state-modifying operation must accept a client-provided `operation_id` (or `Idempotency-Key`). Replaying the same `operation_id` must return the cached result or fail idempotently without creating duplicate ledger records.
- **`I-CONCURRENCY-001` (Row-Level Locking)**: To avoid deadlocks and double-spending, wallets involved in multi-party transfers must always be locked in deterministic order (e.g., ordered by UUID) using `SELECT FOR UPDATE`.

---

## 3. Anti-Fraud & Reliability Invariants

- **`I-FRAUD-001` (Pre-Execution Gate)**: The anti-fraud engine acts as an evaluation gate before domain logic execution. The fraud engine must **never** mutate the ledger or account balances.
- **`I-FRAUD-002` ($O(1)$ Hot Path)**: Fraud rule evaluation on the transaction path must complete in $O(1)$ time without scanning tables, utilizing Caffeine local windows and Redis distributed state.
- **`I-OUTBOX-001` (Guaranteed Event Delivery)**: Domain events must be inserted into the `outbox` table within the same transaction as ledger writes. The Outbox Relay publishes events to NATS JetStream asynchronously with exponential backoff retries.

---

## 4. Engineering & Governance Invariants

- **`I-SDD-001` (Specification First)**: Code implementation must not begin without an approved Specification (`.spec/SPEC-XXX.md`), Architecture Plan (`.spec/PLAN-XXX.md`), and Task List (`.spec/TASKS-XXX.md`).
- **`I-TDD-001` (Test-Driven Verification)**: Tests must be written before implementation. Every functional requirement and invariant must map to at least one automated integration or unit test.
- **`I-OBS-001` (End-to-End Traceability)**: Every request must maintain OpenTelemetry trace context, propagating `operation_id` across database, outbox, and messaging boundaries.
