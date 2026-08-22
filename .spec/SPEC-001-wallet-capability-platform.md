# 📋 Specification: SPEC-001 — Wallet Modular Capability Platform (Spring Modulith)

- **Status**: Draft / Under Clarification
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-22
- **Target Release / Milestone**: Wallet Service V4 — Milestone 0 (Platform Foundation)
- **Architectural Mantra**: *"Capabilities observe, analyze, decide, and propose. Wallet Core authorizes and executes."*

---

## 1. Intent & Business Value

To establish a verified modular architecture within the Wallet Service using **Spring Modulith**. This allows independently evolving financial capabilities (such as automated savings, goal optimization, subscription monitoring, and AI financial agents) to exist as isolated application modules with explicit dependencies, protected domain APIs, and automated structural verification, without adding premature dynamic JAR/plugin complexity.

---

## 2. Conceptual Taxonomy & Modularity Model

```text
┌────────────────────────────────────────────────────────────────────────┐
│ 1. Capability (Business Domain Concept)                                │
│    • Domain logic, financial rules, analysis, proposals               │
│    • Examples: Smart Savings, Goal Strategy, Subscription Intel        │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Packaged as
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 2. Application Module (Spring Modulith Packaging)                      │
│    • In-process module boundary verified by ApplicationModules.verify()│
│    • Examples: br.com.wallet.savings, br.com.wallet.goals              │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Exposed to actors via
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 3. Tool / External Adapter (Interaction Contract)                      │
│    • Protocol adapter exposing capabilities to external actors/AI      │
│    • Examples: MCP Tool (AI Copilot), REST Controller, CLI             │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Package & Module Topology

```text
br.com.wallet
│
├── WalletApplication.java
│
├── [Module 1: br.com.wallet.ledger]       (Core Banking & Transactional Ledger)
│   ├── api/                                (Published Public API)
│   │   ├── TransferFundsUseCase.java       (Use Case Interface / Command)
│   │   ├── DepositFundsUseCase.java        (Use Case Interface / Command)
│   │   ├── WithdrawFundsUseCase.java       (Use Case Interface / Command)
│   │   ├── BalanceUseCase.java             (Query Interface)
│   │   ├── ValidateLedgerUseCase.java      (Audit/Integrity Interface)
│   │   ├── ReplayWalletUseCase.java        (Reconciliation Interface)
│   │   ├── CreateWalletUseCase.java        (Wallet Lifecycle Interface)
│   │   ├── context/                        (Transfer, Deposit, Withdraw, Wallet)
│   │   ├── domain/                         (AccountBalance, LedgerValidationResult, Account, LedgerEntry)
│   │   ├── event/                          (TransferCompletedEvent, MoneyReceivedEvent, EventPublisher)
│   │   ├── exceptions/                     (InsufficientFundsException, BusinessException)
│   │   ├── guard/                          (FraudCheckHelper connecting to :fraud)
│   │   ├── envelope/                       (CommandEnvelope)
│   │   └── utils/                          (JsonUtils, Validations, HashUtils)
│   │
│   └── internal/                           (Protected Sealed Subpackages)
│       ├── service/                        (TransferFundsService, LedgerService, etc.)
│       ├── persistence/                    (AccountDao, LedgerDao, OutboxDao, WalletOperationsDao)
│       ├── outbox/                         (Transactional Outbox & Relay)
│       └── operation/                      (Operation, OperationStatus)
│
├── [Module 2: br.com.wallet.infrastructure] (Framework & Transport Adapters)
│   ├── rest/                               (REST Controllers, OpenAPI Specs, DTOs, Mappers)
│   ├── messaging/                          (NATS JetStream Workers, DLQ, Publishers, Enrichers)
│   ├── persistence/                        (DlqOperationsDao)
│   └── config/                             (NatsConfig, RedisConfig, OpenTelemetry)
│
├── [Subproject :core — Module 3: br.com.wallet.core] (Shared Foundation)
│   ├── context/                            (FraudContext implementing TraceContext)
│   ├── tracing/                            (Traceable, TracingAspect, TraceContext)
│   └── exceptions/                         (IdempotencyException, BusinessException)
│
├── [Subproject :fraud — Module 4: br.com.wallet.fraud] (Anti-Fraud & Risk Engine)
│   ├── domain/                             (FraudEngine, SlidingAmountWindow, FraudDecision, RiskScore)
│   ├── application/                        (FraudService)
│   ├── rules/                              (UserBlockRule, GlobalVelocityRule, SlidingWindowRule)
│   └── infrastructure/                     (RedisUserStore, RedisVelocityStore, LocalStateStore)
│
├── savings                                 (Application Module: Smart Savings)
│   ├── SavingsService.java
│   ├── SavingsRule.java
│   └── internal/
│
├── goals                                   (Application Module: Financial Goals)
│   ├── GoalService.java
│   └── internal/
│
├── intelligence                            (Application Module: Spending & Subs)
│   ├── SubscriptionAnalyzer.java
│   └── internal/
│
└── copilot                                 (Application Module: MCP & AI Edge)
    ├── WalletTools.java
    ├── ProposalService.java
    └── internal/
```

---

## 4. Capability 5-Fold Action Lifecycle

Every capability interaction must map to one of the 5 explicit categories:

```text
CapabilityAction
│
├── QUERY    ──> Read-only lookup of capability state (zero mutation).
│
├── ANALYZE  ──> Derives features and insights from events/state without decisions.
│
├── DECIDE   ──> Evaluates deterministic domain rules producing an actionable choice.
│
├── PROPOSE  ──> Creates a pending FinancialProposal requiring explicit user approval.
│
└── COMMAND  ──> Dispatches a typed, idempotent execution request to core.api.
```

---

## 5. Scope & Non-Goals

### In Scope
- Inclusion of `spring-modulith-starter-core` and `spring-modulith-starter-test` dependencies.
- Reorganization of `:core` into `core.api` (published domain interfaces) and `core.internal` (sealed implementation).
- Automated architectural verification test (`ApplicationModules.of(WalletApplication.class).verify()`) failing the build on any unauthorized module coupling.
- Intra-process event communication using Spring Modulith `@ApplicationModuleListener` with transactional boundary isolation.
- OpenTelemetry trace context and `operation_id` baggage propagation across module boundaries.
- Definition of the `FinancialProposal` primitive lifecycle (`DRAFT` $\rightarrow$ `PENDING_APPROVAL` $\rightarrow$ `APPROVED` / `REJECTED` $\rightarrow$ `EXECUTED` / `FAILED` / `EXPIRED`).

### Non-Goals
- Replacing Transactional Outbox + NATS JetStream for distributed out-of-process messaging (NATS remains the external event backbone).
- Dynamic runtime JAR classloading or OSGi bundles.
- Direct database writes from application modules into `ledger` or `accounts` tables.

---

## 6. Mathematical & System Invariants

- **`I-CAPABILITY-001` (Modulith Architectural Isolation)**: No application module (`savings`, `goals`, `intelligence`, `copilot`) SHALL import, reference, or access `core.internal.*` classes or database repositories directly. All domain interactions MUST use `core.api.*`.
- **`I-CAPABILITY-002` (Core Gate Non-Bypassability)**: All state-modifying operations invoked via `core.api` MUST pass through the standard Core pre-execution pipeline:
  - Idempotency check (`operation_id` uniqueness via `I-IDEMPOTENCY-001`)
  - Anti-Fraud & Risk Engine scoring gate (`I-FRAUD-001` & `I-FRAUD-002`)
  - Row-level database locking (`SELECT FOR UPDATE`) in deterministic order (`I-CONCURRENCY-001`)
  - Balance non-negativity constraint ($\sum \text{Credits} - \sum \text{Debits} \ge 0$, `I-BALANCE-002`)
- **`I-CAPABILITY-003` (Proposal Non-Bypassability)**: A `FinancialProposal` MUST NOT trigger monetary movement until reaching the `APPROVED` state. Approved proposals MUST generate a deterministic `operation_id` linking the execution to the proposal.
- **`I-CAPABILITY-004` (Fault & Transaction Isolation)**: An unhandled exception or latency spike within a module's `@ApplicationModuleListener` MUST NOT abort the primary core transaction or disrupt other modules.

---

## 7. Functional Requirements

- **`REQ-MOD-001` (Modulith Structure Verification)**: The test suite SHALL include an automated architecture test using `ApplicationModules.of(WalletApplication.class).verify()` that fails if any module violates declared package boundaries.
- **`REQ-MOD-002` (Core Published API)**: The `core` module SHALL expose typed use case interfaces and domain events exclusively under `br.com.wallet.core.api`.
- **`REQ-MOD-003` (In-Process Event Dispatch)**: The platform SHALL dispatch domain events (e.g. `MoneyReceivedEvent`, `MoneySentEvent`) to application modules via Spring Modulith event publishing.
- **`REQ-MOD-004` (Module Isolation Tests)**: Every application module SHALL support isolated testing via `@ApplicationModuleTest`.
- **`REQ-MOD-005` (Proposal Lifecycle Service)**: The platform SHALL provide `ProposalService` to manage the lifecycle of `FinancialProposal` records across draft, approval, and execution.
- **`REQ-MOD-006` (Trace Context Propagation)**: The platform MUST propagate `operation_id` and OpenTelemetry span context across in-process module listeners and resulting core commands.

---

## 8. Non-Functional Requirements

- **Performance**: Intra-process event dispatch to module listeners SHALL complete within $< 1\text{ms}$ (P99).
- **Testability**: Architecture verification tests MUST execute in $< 2\text{s}$ during `./gradlew test`.
- **Observability**: Every module listener execution MUST create a child span tagged with `module.name`, `event.type`, `action.type`, and `operation_id`.

---

## 9. Interface Contracts

### 9.1 Core Published API (`br.com.wallet.core.api`)

```java
package br.com.wallet.core.api;

public interface TransferFunds {
    TransferResult execute(TransferCommand command);
}

public interface DepositFunds {
    DepositResult execute(DepositCommand command);
}

public interface WithdrawFunds {
    WithdrawResult execute(WithdrawCommand command);
}

public interface GetBalance {
    BalanceResponse currentBalance(UUID walletId);
    HistoricalBalanceResponse historicalBalance(UUID walletId, Instant atTimestamp);
}
```

### 9.2 Module Event Listener Contract

```java
package br.com.wallet.savings;

import org.springframework.modulith.events.ApplicationModuleListener;
import br.com.wallet.core.api.WalletEvents.MoneyReceivedEvent;

@Service
public class SavingsEventListener {

    private final SavingsService savingsService;

    public SavingsEventListener(SavingsService savingsService) {
        this.savingsService = savingsService;
    }

    @ApplicationModuleListener
    public void onMoneyReceived(MoneyReceivedEvent event) {
        savingsService.evaluateRules(event);
    }
}
```

### 9.3 Architectural Verification Test

```java
package br.com.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    void verifyModulithStructure() {
        ApplicationModules.of(WalletApplication.class).verify();
    }
}
```

---

## 10. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant Enforced |
| :--- | :--- | :--- |
| Module attempts to import `core.internal.LedgerRepository` | Build/test fails immediately during `ModulithArchitectureTest` | `I-CAPABILITY-001` / `REQ-MOD-001` |
| Module listener throws unhandled `RuntimeException` | Exception logged, span marked errored, core transaction unaffected | `I-CAPABILITY-004` |
| Module triggers transfer with duplicate `operation_id` | Core use case rejects idempotently without duplicate ledger records | `I-CAPABILITY-002` / `I-IDEMPOTENCY-001` |
| Unapproved proposal execution attempt | Rejection with `IllegalProposalStateException` | `I-CAPABILITY-003` |

---

## 11. Acceptance Criteria

- [ ] Spring Modulith dependencies (`spring-modulith-starter-core`, `spring-modulith-starter-test`) configured in `build.gradle`.
- [ ] Core package structured with explicit `br.com.wallet.core.api` and `br.com.wallet.core.internal`.
- [ ] Module directories (`savings`, `goals`, `intelligence`, `copilot`) established with explicit boundaries.
- [ ] `ModulithArchitectureTest` executes and passes in the test suite.
- [ ] Isolation test passes demonstrating that a failing module listener does not abort the core transaction.
- [ ] OpenTelemetry trace correlation verified across core event $\rightarrow$ module listener $\rightarrow$ core use case command.
