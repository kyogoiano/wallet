---
name: ledger-engineering
description: Rules, procedures, and integrity calculations for hash-chained financial ledger, accounts balance projections, and tamper-proof verification.
---

# 📒 Ledger Engineering Skill

## 1. Identity & Objective

This skill guides the design, implementation, and verification of the cryptographic **Hash-Chained Transactional Ledger** and **Accounts Projection Model** in Wallet Service.

---

## 2. Core Concepts & Data Model

### The Ledger Model
The `ledger` table is the immutable single source of truth. Every transaction inserts one or more immutable ledger entries:

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `UUID` | Unique entry identifier |
| `wallet_id` | `UUID` | Wallet receiving the credit or debit |
| `amount` | `NUMERIC(19,4)` | Transaction amount |
| `type` | `VARCHAR(20)` | `CREDIT` or `DEBIT` |
| `operation_id` | `UUID` | Client idempotency identifier |
| `sequence` | `BIGINT` | Monotonically increasing sequence per wallet |
| `previous_hash` | `VARCHAR(64)` | SHA-256 hash of previous entry (or genesis constant) |
| `hash` | `VARCHAR(64)` | SHA-256 hash of this entry |
| `created_at` | `TIMESTAMP` | Entry creation timestamp |

---

## 3. Cryptographic Hash Calculation Procedure

### Formula
$$\text{hash} = \text{SHA256}(\text{previous\_hash} + \text{wallet\_id} + \text{normalized\_amount} + \text{type} + \text{operation\_id} + \text{sequence})$$

### Invariant Rules
1. **Genesis Entry**: The very first ledger entry for a wallet uses `previous_hash = "GENESIS"`.
2. **BigDecimal Normalization**: Always strip trailing zeros when converting `BigDecimal` to string to avoid hash divergence between `100.0` and `100.00`:
   ```java
   String normalizedAmount = amount.stripTrailingZeros().toPlainString();
   ```
3. **Deterministic Concatenation**: Concatenate fields strictly in the defined order without nulls.

---

## 4. Atomic Double-Entry Transfers

Transfers between two wallets (Wallet A $\rightarrow$ Wallet B) must:
1. Lock both wallets in alphabetical UUID order (`SELECT ... FOR UPDATE`).
2. Verify Wallet A has $\text{Balance}(A) \ge \text{amount}$.
3. Deduct from `accounts` for A and add to `accounts` for B.
4. Insert `DEBIT` entry on ledger for A with next sequence and chained hash.
5. Insert `CREDIT` entry on ledger for B with next sequence and chained hash.
6. Insert domain event into `outbox`.
7. Commit in a single transaction.

---

## 5. Ledger Integrity Verification

To validate ledger integrity:
1. Read all entries for a wallet ordered by `sequence ASC`.
2. Verify `entry[0].previous_hash == "GENESIS"`.
3. For $i > 0$, verify `entry[i].previous_hash == entry[i-1].hash`.
4. Recalculate hash for each entry and assert `calculated_hash == entry.hash`.
5. Sum all credits minus debits and assert `sum == account.balance`.
