# 📋 Specification: SPEC-000 — Architecture Alignment & Modulith Core Baseline (Refactoring)

- **Status**: Ratified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-22
- **Target Release / Milestone**: Wallet Service V4 — Milestone 0.0 (Baseline Architecture Alignment)
- **Architectural Mantra**: *"Clean boundaries make reliable systems. Verify architecture automatically."*

---

## 1. Intent & Business Value

Before introducing new business capabilities (Smart Savings, Goal Engine, Subscription Intelligence, AI Copilot), we must align our existing production codebase (the root application, `:core` subproject, and `:fraud` subproject) into a coherent, verifiable **Spring Modulith baseline**.

Currently, the root application contains clean architecture packages (`application`, `domain`, `infrasctructure`, `interfaces`) mixed at the top level alongside standalone Gradle subprojects `:core` and `:fraud`. `SPEC-000` establishes the formal internal module boundaries, establishes `core.api` vs `core.internal`, fixes package typos, adds Spring Modulith starter dependencies, and integrates `ApplicationModules.of(WalletApplication.class).verify()` into the test suite with zero regression to existing financial invariants.

---

## 2. Current Baseline vs Target Modulith Structure

### 2.1 Current State Analysis
- **Root Application (`:`)**:
  - `br.com.wallet.application` (contains use case interfaces and service implementations)
  - `br.com.wallet.domain` (contains `Account`, `LedgerEntry`, domain contexts, and events)
  - `br.com.wallet.infrasctructure` (contains DAOs, NATS messaging consumers/publishers, and Outbox)
  - `br.com.wallet.interfaces.rest` (contains REST controllers, DTOs, and mappers)
  - `br.com.wallet.config` (contains NATS, Redis, and OTel configurations)
- **Subproject `:core`**:
  - Shared cross-cutting concerns: `Traceable`, `TracingAspect`, `TraceContext`, `IdempotencyException`.
- **Subproject `:fraud`**:
  - Standalone Anti-Fraud engine: `FraudEngine`, `SlidingAmountWindow`, rules (`UserBlockRule`, `GlobalVelocityRule`), and state stores.

## 2. Target Spring Modulith Architecture (4-Module DAG)

```text
br.com.wallet
│
├── [Root Application: br.com.wallet]
│   └── WalletApplication.java              (Spring Boot Entry Point)
│
├── [Module 1: br.com.wallet.ledger]       (Core Banking & Transactional Ledger Module)
│   ├── api                                 (Published Public Interface)
│   │   ├── TransferFundsUseCase.java       (Use Case Interface)
│   │   ├── DepositFundsUseCase.java        (Use Case Interface)
│   │   ├── WithdrawFundsUseCase.java       (Use Case Interface)
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
│   └── internal                            (Protected Implementation Packages)
│       ├── service/                        (TransferFundsService, LedgerService, etc.)
│       ├── persistence/                    (AccountDao, LedgerDao, OutboxDao, WalletOperationsDao)
│       ├── outbox/                         (OutboxRelay, OutboxEvent, OutboxStatus)
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
└── [Future Peer Modules: savings, goals, intelligence, copilot]
```

---

## 3. Scope & Non-Goals

### In Scope
- Add `spring-modulith-starter-core`, `spring-modulith-starter-test`, and `jacoco` to `build.gradle`.
- Restructure core domain logic in root `src/` into explicit `ledger.api` (published contracts) and `ledger.internal` (sealed persistence and use case services).
- Fix naming typos (e.g. `infrasctructure` $\rightarrow$ `infrastructure`).
- Ensure REST controllers and NATS message consumers consume `ledger.api` interfaces rather than internal services/DAOs.
- Maintain existing subproject boundaries (`:core` for cross-cutting tracing/exceptions, `:fraud` for anti-fraud engine).
- Implement `ModulithArchitectureTest` asserting `ApplicationModules.of(WalletApplication.class).verify()`.
- Ensure 100% test passing across all existing unit, integration, and Testcontainers tests with zero regression.

### Non-Goals
- Implementing new business features (Smart Savings, Goal Engine are reserved for `SPEC-002`, `SPEC-003`).
- Modifying SQL schemas, hash calculation algorithms, or database migration scripts.
- Modifying the `:fraud` module's internal rule evaluation algorithms.

---

## 4. Mathematical, Architectural & System Invariants

- **`I-LEDGER-001` (Immutable Source of Truth)**: Ledger append-only property remains untouched.
- **`I-LEDGER-002` (Hash-Chaining Integrity)**: $\text{hash}_n = \text{SHA256}(\text{hash}_{n-1} + \text{walletId} + \text{amount} + \text{type} + \text{operationId} + \text{sequence})$ calculation logic is preserved identically in `ledger.internal.utils.HashUtils`.
- **`I-BALANCE-001` & `I-BALANCE-002` (Balance Math & Non-Negativity)**: Account balance projections and validation rules are strictly maintained.
- **`I-ATOMICITY-001` & `I-IDEMPOTENCY-001`**: Transactional boundaries with `SELECT FOR UPDATE` locking and `operation_id` deduplication remain enforced.
- **`I-FRAUD-001` & `I-FRAUD-002`**: Pre-execution fraud evaluation gate in `ledger.api.guard.FraudCheckHelper` executes in $O(1)$ prior to transaction lock acquisition.
- **`I-MODULITH-001` (Internal Encapsulation)**: No module outside `ledger` SHALL directly access types contained in `br.com.wallet.ledger.internal.*`.
- **`I-MODULITH-002` (Published API Access)**: Cross-module interactions with the ledger engine SHALL occur exclusively through `br.com.wallet.ledger.api.*`.

---

## 5. Functional Requirements

- **`REQ-ALIGN-001` (Ledger API Definition)**: The system SHALL expose all core banking operations via typed interfaces in `br.com.wallet.ledger.api` (`TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`, `BalanceUseCase`, `ValidateLedgerUseCase`, `ReplayWalletUseCase`, `CreateWalletUseCase`).
- **`REQ-ALIGN-002` (Ledger Internal Encapsulation)**: All database repositories (`AccountDao`, `LedgerDao`, `OutboxDao`, `WalletOperationsDao`), service implementations, and internal domain models SHALL reside under `br.com.wallet.ledger.internal.*`.
- **`REQ-ALIGN-003` (Adapter Decoupling)**: REST controllers (`infrastructure.rest`) and NATS consumers (`infrastructure.messaging`) SHALL only inject and invoke interfaces from `ledger.api`.
- **`REQ-ALIGN-004` (Automated Architecture Verification)**: The test suite SHALL execute `ModulithArchitectureTest` on every build, asserting zero illegal cross-package access.
- **`REQ-ALIGN-005` (Zero Regression)**: All existing integration scenarios (`TransferFundsIT`, `LedgerIT`, `ValidateLedgerIT`, `SlidingWindowRuleTest`, `OutboxIT`) MUST pass without modification to business expectations.

---

## 6. Non-Functional Requirements

- **Performance**: Zero runtime overhead introduced by the architectural reorganization.
- **Maintainability**: Clear module isolation verifiable via Spring Modulith documentation generation (`new Documenter(modules).writeDocumentation()`).
- **Observability**: OpenTelemetry tracing spans and baggage propagation across controller $\rightarrow$ use case $\rightarrow$ DAO $\rightarrow$ outbox remain 100% functional.

---

## 7. Interface Contracts

### 7.1 Ledger Published API (`br.com.wallet.ledger.api`)

```java
package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.context.*;
import br.com.wallet.ledger.api.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TransferFundsUseCase {
    void handle(Transfer transfer);
}

public interface DepositFundsUseCase {
    void handle(Deposit deposit);
}

public interface WithdrawFundsUseCase {
    void handle(Withdraw withdraw);
}

public interface BalanceUseCase {
    BigDecimal getBalance(UUID walletId);
    BigDecimal getHistoricalBalance(UUID walletId, Instant at);
}

public interface ValidateLedgerUseCase {
    LedgerValidationResult execute(UUID walletId);
}

public interface ReplayWalletUseCase {
    BigDecimal execute(UUID walletId);
}
```

### 7.2 Architecture Verification Test

```java
package br.com.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(WalletApplication.class);

    @Test
    void verifyArchitecture() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualFilesAsPlantUml();
    }
}
```

---

## 8. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant Enforced |
| :--- | :--- | :--- |
| External adapter attempts to inject `AccountDao` directly | Spring Modulith verification test fails immediately | `I-MODULITH-001` / `REQ-ALIGN-004` |
| Invalid transfer with negative amount or self-transfer | Ledger API rejects with `IllegalArgumentException` | `I-BALANCE-002` |
| Duplicate `operation_id` on transfer command | Core use case returns cached response or rejects idempotently | `I-IDEMPOTENCY-001` |
| Fraud engine blocks transaction | Core use case throws `FraudBlockedException` before acquiring DB lock | `I-FRAUD-001` |

---

## 9. Acceptance Criteria

- [x] Spring Modulith starters added to `build.gradle`.
- [x] Root `src/` restructured: `ledger.api` (published API), `ledger.internal` (sealed implementation), `infrastructure` (rest, messaging, config).
- [x] Package typo `infrasctructure` renamed to `infrastructure`.
- [x] `:core` (shared foundation) and `:fraud` (anti-fraud engine) subprojects cleanly linked and validated.
- [x] `ModulithArchitectureTest` passes with clean verification (0 violations, 0 cycles).
- [x] All unit, integration, and Testcontainers test suites pass completely (`./gradlew test`).
- [x] Complete execution summary generated in `.spec/summaries/SUMMARY-000-architecture-alignment-modulith-baseline.md`.
