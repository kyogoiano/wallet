# 🎭 Focusing Behavior over Implementation in Spec Extraction

Financial systems must be specified and verified by their **observable contracts and invariant properties**, not their internal implementation mechanics. Focusing on implementation details creates brittle specs that break upon routine refactoring and encourages "vibe coding".

---

## 🧭 Core Principles

1. **Black-Box Observable Verification**:
   - A specification requirement must be verifiable by observing:
     - Public API inputs and outputs (HTTP status codes, response payloads).
     - Published domain events (NATS JetStream messages, Modulith application events).
     - Durable state projections (Account balance, ledger hash chain continuity, outbox records).
     - Observable domain exceptions (`InsufficientFundsException`, `AccountBlockedException`).
2. **Encapsulation Protection**:
   - Specifications must treat the internal architecture of a capability module as a black box.
   - Never assert private method calls, internal field mutations, or helper class interactions in functional requirements.
3. **Refactoring Invariance**:
   - A pure refactoring (e.g. migrating from imperative loops to Java Stream API, or switching from an in-memory cache to DragonflyDB) should require **zero changes** to the Product Specification (`SPEC-XXX.md`).

---

## 📋 Transformation Guide: Implementation $\to$ Behavior

| Implementation-Polluted Requirement (BAD) | Observable Behavioral Requirement (GOOD) |
| :--- | :--- |
| "The system will instantiate a `FraudGate` bean and call `evaluate()` with a `HashMap` of context attributes." | "Every monetary transaction SHALL pass through pre-execution risk evaluation. If risk decision is `HARD_BLOCK`, the transaction MUST be rejected immediately with `403 Forbidden` and zero ledger record created." |
| "The query should execute `SELECT balance FROM accounts WHERE id = ? FOR UPDATE`." | "Concurrent operations involving the same wallet SHALL execute with serializable isolation, preventing double-spending and deadlocks." |
| "The controller will throw an `AccountBlockedException` if `account.getStatus().equals('BLOCKED')`." | "Any monetary operation involving an account whose lifecycle status is not `ACTIVE` (e.g. `BLOCKED`, `SUSPENDED`, `FROZEN`) MUST be rejected immediately per `I-ACCOUNT-001`." |
| "Calculate round-up using `Math.round()` and insert a row in `savings_executions`." | "Micro-savings sweeps SHALL sweep the exact delta to the next integer currency unit ($\Delta = \lceil A \rceil - A$) using scale 2 `BigDecimal` arithmetic. Zero-cent deltas ($\Delta = 0.00$) SHALL produce no sweep." |

---

## 🧪 Test Triad Alignment

Focusing on behavior enables the **Deterministic Test Triad (`I-TDD-002`)**:

```text
┌──────────────────────────────────────────────────────────────┐
│ 1. Canonical Positive Path:                                   │
│    Given active account with balance 100.00                   │
│    When valid withdrawal of 40.00 is executed                 │
│    Then balance becomes 60.00 and ledger contains DEBIT 40.00 │
├──────────────────────────────────────────────────────────────┤
│ 2. Invalid Input Gate:                                        │
│    When withdrawal of -10.00 is attempted                     │
│    Then reject with 400 Bad Request and zero ledger mutation  │
├──────────────────────────────────────────────────────────────┤
│ 3. Invariant Breach Gate:                                     │
│    When withdrawal of 150.00 is attempted from balance 100.00│
│    Then reject with 422 InsufficientFundsException            │
└──────────────────────────────────────────────────────────────┘
```
