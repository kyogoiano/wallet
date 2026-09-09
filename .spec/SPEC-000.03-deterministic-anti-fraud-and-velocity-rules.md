# 📐 Specification: SPEC-000.03 — Deterministic Anti-Fraud Pre-Execution Gate & Velocity Rules

- **Initiative**: Anti-Fraud & Risk Assessment (`br.com.wallet.fraud`, `br.com.wallet.ledger.api.guard`)
- **Status**: 🟢 **Implemented & Verified (Reverse-Engineered)**
- **Baseline**: Wallet Service V1/V2 Deterministic Anti-Fraud Engine
- **Associated Plan**: [`plans/PLAN-000.03-deterministic-anti-fraud-and-velocity-rules.md`](file:///.spec/plans/PLAN-000.03-deterministic-anti-fraud-and-velocity-rules.md)
- **Associated Tasks**: [`tasks/TASKS-000.03-deterministic-anti-fraud-and-velocity-rules.md`](file:///.spec/tasks/TASKS-000.03-deterministic-anti-fraud-and-velocity-rules.md)
- **Execution Summary**: [`summaries/SUMMARY-000.03-deterministic-anti-fraud-and-velocity-rules.md`](file:///.spec/summaries/SUMMARY-000.03-deterministic-anti-fraud-and-velocity-rules.md)

---

## 1. Intent & Business Value

### 1.1 Problem Statement
Financial platforms are constant targets for automated credential stuffing, rapid account draining, and organized money-mule rings. Evaluating fraud after ledger mutations leads to costly chargebacks and complex rollback transactions, while slow fraud checks on the write path degrade system throughput.

### 1.2 Solution Intent
Establish an ultra-low latency, deterministic **Pre-Execution Anti-Fraud Gate** (`FraudCheckHelper`) that evaluates transactional risk before database row locking and ledger mutation. Combine Caffeine local sliding memory buffers with DragonflyDB/Redis atomic Lua scripts to evaluate velocity and recipient risk in $O(1)$ time, immediately rejecting high-risk operations with `FraudBlockedException` and enforcing dual-store user blocking.

---

## 2. Mathematical Invariants

- **`I-FRAUD-001` (Zero Ledger Mutation Invariant)**: The anti-fraud engine acts strictly as an evaluation gate. Under no circumstances may fraud evaluation mutate `ledger` entries or `accounts` balances.
- **`I-FRAUD-002` ($O(1)$ Hot Path Latency)**: Fraud rule evaluation on the transaction path must complete in $O(1)$ time without database table scans:
  $$\text{Complexity}(\text{evaluate}) = O(1), \quad P99 < 2\text{ms}$$
- **`I-FRAUD-003` (Deterministic Score Aggregation & Decision Boundary)**:
  $$\text{Score} = \sum_{r \in \text{Rules}} \text{scoreImpact}(r)$$
  $$\text{Decision} = \begin{cases} \text{ALLOW}, & \text{Score} < 40 \\ \text{REVIEW}, & 40 \le \text{Score} < 80 \\ \text{BLOCK}, & \text{Score} \ge 80 \end{cases}$$
- **`I-FRAUD-004` (Dual-Store Account Blocking Gate)**: A `BLOCK` decision immediately invokes fail-closed blocking:
  $$\text{Block}(u) \implies \text{PostgreSQL}(\text{status} = \text{'BLOCKED'}) \land \text{Redis}(\text{user}:u:\text{blocked} = \text{true})$$
- **`I-FRAUD-005` (Replay Neutrality)**: Re-evaluating an identical `operation_id` within the velocity window must return neutral impact without double-counting:
  $$\text{exists}(\text{operationId}) \implies \Delta \text{Score} = 0, \quad \Delta \text{Count} = 0$$

---

## 3. MoSCoW Requirements

### 3.1 Product Intent (Observable Behavior)
- **`REQ-FRD-001` [MUST]**: Transactions where user is flagged as blocked (`UserBlockRule`) MUST contribute +30 risk score points (or trigger immediate gate rejection).
- **`REQ-FRD-002` [MUST]**: User transaction velocity exceeding 10 operations within a 30-second sliding window (`GlobalVelocityRule`) MUST contribute +30 risk score points.
- **`REQ-FRD-003` [MUST]**: Cumulative transaction amount exceeding 10,000.00 within the local sliding window (`SlidingWindowRule`) MUST contribute +10 risk score points.
- **`REQ-FRD-004` [MUST]**: High-risk recipient patterns (`NewRecipientRule`) MUST score rings (up to +50), mules (up to +20), and fan-outs (up to +15).
- **`REQ-FRD-005` [MUST]**: If aggregate score $\ge 80$, the gate MUST synchronously block the account in PostgreSQL and Redis and throw `FraudBlockedException`.
- **`REQ-FRD-006` [MUST]**: Replaying an already registered `operation_id` in Redis velocity sorted set MUST return `VelocityResult.Replay` with 0 score impact.
- **`REQ-FRD-007` [SHOULD]**: User block status cache (`RedisUserStore`) MUST employ asymmetric TTLs (10 min for blocked, 30s for unblocked).
- **`REQ-FRD-008` [COULD]**: Asynchronous fraud review events logged to `outbox` table (`FraudEvent`) for compliance and downstream reporting.
- **`REQ-FRD-009` [WON'T]**: Blocking transactions solely based on asynchronous ML embeddings or offline graph mining without deterministic rule triggers.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Affected Component | Nature of Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| **Ledger Use Cases** | Executes check before ledger lock acquisition | Pre-check prevents database lock holding during external cache queries |
| **Account Lifecycle** | `BLOCK` decision mutates account status to `BLOCKED` | `I-ACCOUNT-001` gate blocks any future transfers, deposits, or withdrawals |
| **Dragonfly / Redis** | Velocity and block status queried on every write | Atomic Lua script handles expiration, cleanup, and scoring in single network hop |
| **Outbox Relay** | `FraudEvent` written to outbox on every evaluation | Recorded in outbox for asynchronous audit without blocking transaction path |

---

## 5. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-FRD-001` / `005` | `AccountBlockingIT.shouldRejectTransferFromBlockedAccount()` | Unregistered user $\to$ evaluate as unblocked | Blocked account $\to$ `AccountBlockedException` |
| `REQ-FRD-002` (Velocity) | `GlobalVelocityRuleTest.shouldTriggerWhenExceedingThreshold()` | Null operation ID $\to$ reject evaluation | $> 10$ tx / 30s $\to$ +30 score points |
| `REQ-FRD-003` (Sliding) | `SlidingWindowRuleTest.shouldTriggerWhenExceedingAmount()` | Negative amount $\to$ 0 contribution | $> 10,000.00 \to$ +10 score points |
| `REQ-FRD-006` (Replay) | `GlobalVelocityRuleTest.shouldNotTriggerOnReplay()` | Duplicate `operation_id` $\to$ `Replay` result | Replayed request $\to$ 0 score impact |

---

## 6. Acceptance Criteria

- [x] Pre-execution gate evaluates before ledger locks are acquired (`FraudCheckHelper`).
- [x] Sliding amount window uses ring buffer array with `LongAdder` running total (`SlidingAmountWindowTest`).
- [x] Redis velocity tracking runs atomic Lua script with `zadd NX` deduplication (`RedisVelocityStore`).
- [x] Fraud blocked users are locked synchronously in PostgreSQL and Redis (`FraudReactionIT`).
- [x] Unit and integration test suites pass green with $\ge 85\%$ coverage across fraud rules.
