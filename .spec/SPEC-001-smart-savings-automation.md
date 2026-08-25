# 📋 Specification: SPEC-001 — Smart Savings Automation (br.com.wallet.savings)

- **Status**: Completed / Verified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-22
- **Target Release / Milestone**: Wallet Service V4 — Phase 1 (First Business Capability Module)
- **Architectural Mantra**: *"Capabilities observe, analyze, and decide. The Financial Core authorizes and executes."*

---

## 1. Intent & Business Value

To introduce **Smart Savings Automation** as the first concrete peer business capability module (`br.com.wallet.savings`) in the Wallet Service. 

Using Spring Modulith application modules and intra-process domain events (`@ApplicationModuleListener`), the `savings` module passively observes banking events (`DepositCompletedEvent`, `TransferCompletedEvent`) and executes automated, deterministic savings strategies without touching sealed financial ledger internals or modifying the core accounting model.

```
                    ledger (Financial Core)
                       │
                       │ DepositCompletedEvent (origin=USER)
                       │ TransferCompletedEvent (origin=USER)
                       ▼
                ┌──────────────┐
                │   savings    │
                │              │
                │ observe      │
                │ analyze      │
                │ decide       │
                └──────┬───────┘
                       │
                       │ TransferFundsUseCase.handle(Transfer)
                       │ (operation_id=deterministic, origin=SAVINGS_AUTOMATION)
                       ▼
                ┌──────────────┐
                │    ledger    │
                │              │
                │ authorize    │
                │ fraud gate   │
                │ idempotency  │
                │ ACID execute │
                └──────────────┘
```

---

## 2. Core Business Strategies & Rules

```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. Round-Up Savings Rule (Micro-Savings)                               │
│    • Triggers on: TransferCompletedEvent with origin == USER           │
│    • Logic: Rounds transaction to nearest step (e.g., R$ 1.00, R$ 5.00)│
│    • Example: Spend R$ 47.30 (step=5.00) → Round to 50.00 → Save R$ 2.70│
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ 2. Income Percentage Sweep Rule (Pay Yourself First)                   │
│    • Triggers on: DepositCompletedEvent with origin == USER            │
│    • Logic: Allocates fixed percentage (e.g. 10%) to Savings Wallet    │
│    • Example: Deposit R$ 5,000.00 → Auto-save R$ 500.00                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ 3. Threshold Excess Sweep Rule (Balance Ceiling)                       │
│    • Triggers on: DepositCompletedEvent with origin == USER            │
│    • Logic: Evaluates current available balance vs ceiling threshold L │
│    • Example: Limit R$ 10,000.00, Current R$ 12,500.00 → Save R$ 2,500 │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Package & Module Topology

The `savings` module is organized as a first-class peer Spring Modulith module under `br.com.wallet.savings`:

```text
br.com.wallet.savings
│
├── package-info.java                   (@ApplicationModule(allowedDependencies = {"ledger::api", "ledger", "core::api", "core"}))
│
├── api                                 (Published Public Interface)
│   ├── SavingsPlanUseCase.java         (Configure & Manage Savings Plans)
│   ├── SavingsQueryUseCase.java        (Query accumulated savings metrics & history)
│   ├── model/                          (SavingsPlanDto, SavingsRuleDto, SavingsMetricsDto, OperationOrigin)
│   └── dto/                            (CreateSavingsPlanCommand, SavingsMetricsResponse, SavingsHistoryResponse)
│
└── internal                            (Protected Implementation Packages)
    ├── domain/                         (SavingsPlan, SavingsRule, SavingsRuleType, SavingsExecutionHistory)
    ├── application/                    (SavingsPlanService, SavingsExecutionService)
    ├── listener/                       (SavingsEventListener with @ApplicationModuleListener)
    ├── engine/                         (SavingsRuleEngine, RoundUpCalculator, PercentageCalculator, ThresholdCalculator)
    └── persistence/                    (SavingsPlanDao, SavingsExecutionHistoryDao)
```

---

## 4. Architectural Decisions & Clarifications (Gate 1)

| ID | Topic | Decision & Semantic Specification |
| :--- | :--- | :--- |
| `CLAR-SAV-001` | **Eligible Transfer Filtering** | Round-Up ONLY evaluates outgoing transfers with `origin == USER`. Reversals, capability sweeps, and automated operations are ignored. |
| `CLAR-SAV-002` | **Origin & Causality Metadata** | Every financial event and command carries `OperationOrigin` (`USER`, `SAVINGS_AUTOMATION`, `SYSTEM`, `REVERSAL`). |
| `CLAR-SAV-003` | **Threshold Balance Evaluation** | Threshold Sweep evaluates the **current available balance** via `BalanceUseCase.getBalance(walletId)` at the time of async execution. |
| `CLAR-SAV-004` | **Monetary Precision & Rounding** | Canonical `BigDecimal` representation with 2 decimal places and `RoundingMode.HALF_EVEN`. Floating-point math is strictly forbidden. |
| `CLAR-SAV-005` | **Multi-Rule Evaluation Order** | Rules for a wallet execute deterministically in sequence: (1) Percentage Sweep $\rightarrow$ (2) Threshold Sweep $\rightarrow$ (3) Round-Up. |
| `CLAR-SAV-006` | **Savings Wallet Model** | Target savings wallet is a standard `walletId` linked in `SavingsPlan`. No special ledger account types or schema mutations are needed. |
| `CLAR-SAV-007` | **Fault & Retry Semantics** | `@ApplicationModuleListener` failures are isolated; primary transactions never abort. Failed sweeps are logged for observability. |
| `CLAR-SAV-008` | **History & Idempotency** | Execution history provides auditability. Command deduplication is guaranteed deterministically via SHA-256 operation IDs. |

---

## 5. Mathematical & System Invariants

- **`I-SAVINGS-001` (Deterministic Idempotency & Origin Loop Prevention)**:
  Savings sweeps dispatched to `ledger.api.TransferFundsUseCase` MUST carry a deterministic `operation_id` and explicit origin:
  $$\text{operationId}_{\text{savings}} = \text{SHA256}(\text{sourceOperationId} + \text{ruleId} + \text{SAVINGS\_SWEEP})$$
  $$\text{origin} = \text{OperationOrigin.SAVINGS\_AUTOMATION}$$
  Events arriving with `origin == SAVINGS_AUTOMATION` MUST be ignored immediately in $O(1)$ to prevent cascading loops.
- **`I-SAVINGS-002` (Zero Direct Ledger Mutation)**:
  The `savings` module MUST NOT access `br.com.wallet.ledger.internal.*` or write directly to `ledger` or `accounts` tables (`I-MODULITH-001`). All transfers MUST execute via `ledger.api.TransferFundsUseCase` (`I-MODULITH-002`).
- **`I-SAVINGS-003` (Transaction & Fault Isolation)**:
  A failure, exception, or rejection during savings rule evaluation or sweep dispatch MUST NOT abort the primary deposit or transfer transaction that triggered the event.
- **`I-SAVINGS-004` (Liquidity Intent Protection)**:
  The `savings` engine SHALL calculate the sweep amount respecting the configured `minimumRetainedBalance`:
  $$\text{maximumSweep} = \max\left(0, \text{currentBalance} - \text{minimumRetainedBalance}\right)$$
  $$\text{actualSweep} = \min\left(\text{calculatedSweep}, \text{maximumSweep}\right)$$
  If $\text{actualSweep} == 0$, the sweep is skipped. The Financial Core remains the final authoritative enforcement point for balance validity.
- **`I-SAVINGS-005` (Eventual Capability Consistency)**:
  Savings automation is eventually consistent with the financial events that trigger it. Rule evaluation operates on the state observable at processing time, while the Financial Core remains the authoritative source for final transaction validity.

---

## 6. Functional Requirements

- **`REQ-SAV-001` (Savings Plan Lifecycle)**: The system SHALL allow users to create, view, pause, resume, and delete savings plans linking a `sourceWalletId` to a `targetWalletId` with an optional `minimumRetainedBalance`.
- **`REQ-SAV-002` (Asynchronous Event Processing)**: The `savings` module SHALL listen for `DepositCompletedEvent` and `TransferCompletedEvent` using `@ApplicationModuleListener`.
- **`REQ-SAV-002A` (Eligible Transaction Classification)**: The system SHALL only evaluate rules against transactions with `origin == USER`. Events marked with `SAVINGS_AUTOMATION`, `SYSTEM`, or `REVERSAL` SHALL be skipped immediately.
- **`REQ-SAV-003` (Round-Up Micro-Savings Calculation)**: For Round-Up rules on eligible transfers, the system SHALL calculate:
  $$\text{sweepAmount} = \lceil \text{amount} / \text{step} \rceil \times \text{step} - \text{amount}$$
- **`REQ-SAV-003A` (Strictly Positive Step & Zero-Sweep Guard)**: The round-up `step` MUST be strictly positive ($\text{step} > 0$, e.g. 1.00, 5.00, 10.00). If $\text{sweepAmount} == 0$, no financial command SHALL be dispatched.
- **`REQ-SAV-003B` (Threshold Ceiling Evaluation)**: For Threshold rules on deposits, the system SHALL query `BalanceUseCase.getBalance(walletId)` and calculate:
  $$\text{sweepAmount} = \max\left(0, \text{currentBalance} - \text{ceilingThreshold}\right)$$
- **`REQ-SAV-004` (Percentage Sweep Calculation)**: For Percentage rules on deposits, the system SHALL calculate:
  $$\text{sweepAmount} = \text{depositAmount} \times \frac{\text{percentage}}{100}$$
- **`REQ-SAV-004A` (Canonical Monetary Rounding)**: All currency calculations SHALL use canonical 2-decimal scale with `RoundingMode.HALF_EVEN`. Floating-point arithmetic is forbidden.
- **`REQ-SAV-005` (Ordered Rule Evaluation)**: When multiple rules match an event, the system SHALL evaluate them in strict deterministic order: Percentage $\rightarrow$ Threshold $\rightarrow$ Round-Up.
- **`REQ-SAV-006` (Idempotent Sweep Dispatch)**: When $\text{actualSweep} > 0$, the system SHALL invoke `ledger.api.TransferFundsUseCase` with the deterministic `operation_id` and `origin = SAVINGS_AUTOMATION`.
- **`REQ-SAV-007` (Audit Trail & Metrics)**: The system SHALL record every evaluated sweep attempt in `savings_execution_history` (status: `EXECUTED`, `SKIPPED_LIQUIDITY`, `FAILED`) and provide aggregated metrics.
- **`REQ-SAV-009` (Deterministic Rule Evaluation Order)**: For a given triggering event, applicable savings rules SHALL be evaluated in a deterministic and documented order. For deposit events, Percentage Sweep SHALL be evaluated before Threshold Excess Sweep.

---

## 7. Non-Functional Requirements

- **Performance**: Pure deterministic in-memory rule calculation SHALL complete within $< 2\text{ms}$ ($O(1)$) under defined workload. Persistence, event delivery, and Financial Core execution are excluded from this measurement.
- **Modularity**: `ModulithArchitectureTest.verifyArchitecture()` MUST confirm clean acyclic boundaries: `savings` $\rightarrow$ `ledger::api`, `core::api`.
- **Observability**: Every savings sweep span MUST record attributes:
  - `savings.plan_id`
  - `savings.rule_id`
  - `savings.rule_type`
  - `savings.source_operation_id`
  - `savings.sweep_amount`
  while propagating `operation_id` across the trace.

---

## 8. Interface Contracts

### 8.1 Public API (`br.com.wallet.savings.api`)

```java
package br.com.wallet.savings.api;

import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import java.util.List;
import java.util.UUID;

public interface SavingsPlanUseCase {
    SavingsPlanDto createPlan(CreateSavingsPlanCommand command);
    void pausePlan(UUID planId);
    void resumePlan(UUID planId);
    void deletePlan(UUID planId);
    List<SavingsPlanDto> getPlansForWallet(UUID walletId);
}

public interface SavingsQueryUseCase {
    SavingsMetricsResponse getMetrics(UUID walletId);
}
```

### 8.2 Operation Origin Model (`br.com.wallet.core.context` / `br.com.wallet.savings.api.model`)

```java
package br.com.wallet.savings.api.model;

public enum OperationOrigin {
    USER,
    SAVINGS_AUTOMATION,
    SYSTEM,
    REVERSAL
}
```

### 8.3 Event Listener Contract (`br.com.wallet.savings.internal.listener`)

```java
package br.com.wallet.savings.internal.listener;

import br.com.wallet.ledger.api.event.DepositCompletedEvent;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.savings.api.model.OperationOrigin;
import org.springframework.modulith.events.ApplicationModuleListener;

public class SavingsEventListener {

    @ApplicationModuleListener
    public void onDeposit(DepositCompletedEvent event) {
        if (event.origin() != OperationOrigin.USER) return;
        // Evaluate active percentage and threshold sweep rules in deterministic order
    }

    @ApplicationModuleListener
    public void onTransfer(TransferCompletedEvent event) {
        if (event.origin() != OperationOrigin.USER) return;
        // Evaluate active round-up rules
    }
}
```

---

## 9. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant Enforced |
| :--- | :--- | :--- |
| Transfer event has `origin == SAVINGS_AUTOMATION` | Listener immediately returns without evaluating rules | `I-SAVINGS-001` (Loop Prevention) |
| Sweep amount exceeds liquidity (`balance - minRetained <= 0`) | Sweep skipped; recorded as `SKIPPED_LIQUIDITY`; primary transaction unaffected | `I-SAVINGS-003`, `I-SAVINGS-004` |
| Round-up calculated on exact multiple (e.g. 50.00, step=5.00) | `sweepAmount` is 0.00; no transfer command dispatched | `REQ-SAV-003A` |
| Target savings wallet locked or unavailable | Ledger API throws exception; caught and logged in savings history; primary transaction succeeds | `I-SAVINGS-003` |
| Direct import of `br.com.wallet.ledger.internal.*` in savings | Build fails during `ModulithArchitectureTest` | `I-SAVINGS-002`, `I-MODULITH-001` |

---

## 10. Acceptance Criteria

- [x] `br.com.wallet.savings` created with `package-info.java` defining allowed dependencies on `ledger::api` and `core::api`.
- [x] `SavingsPlanUseCase` and `SavingsQueryUseCase` exposed under `savings.api`.
- [x] `OperationOrigin` enum introduced and integrated into event evaluation.
- [x] Round-Up, Percentage, and Threshold calculators implemented with `HALF_EVEN` monetary rounding.
- [x] Liquidity intent protection and ordered rule evaluation implemented (`REQ-SAV-009`).
- [x] Non-re-entrant loop prevention (`I-SAVINGS-001`) verified with unit and integration tests.
- [x] `ModulithArchitectureTest.verifyArchitecture()` passes with 0 violations.
- [x] JaCoCo test coverage meets $\ge 85\%$ for `savings` domain and rule engine.
