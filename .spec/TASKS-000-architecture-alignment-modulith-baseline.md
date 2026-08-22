# 📝 Task Breakdown: TASKS-000 — Architecture Alignment & Modulith Core Baseline

- **Associated Spec**: [`SPEC-000-architecture-alignment-modulith-baseline.md`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md)
- **Associated Plan**: [`PLAN-000-architecture-alignment-modulith-baseline.md`](file:///.spec/PLAN-000-architecture-alignment-modulith-baseline.md)
- **Status**: In Progress / Verification
- **Author**: Antigravity Financial Architecture Team

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-ALIGN-001` | `TransferFundsTest`, `BalanceServiceTest` | `TASK-0.2`, `TASK-0.3` |
| `REQ-ALIGN-002` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-0.1`, `TASK-0.4` |
| `REQ-ALIGN-003` | `OperationsControllerTest`, `TransferConsumerTest` | `TASK-0.5`, `TASK-0.6` |
| `REQ-ALIGN-004` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-0.7` |
| `REQ-ALIGN-005` | Full Test Suite (`./gradlew test`) | `TASK-0.8` |
| `I-LEDGER-001` | `LedgerValidationServiceTest` | `TASK-0.3`, `TASK-0.8` |
| `I-LEDGER-002` | `HashUtilTest`, `LedgerScenarioTest` | `TASK-0.3`, `TASK-0.8` |
| `I-BALANCE-001` | `BalanceScenarioTest` | `TASK-0.3`, `TASK-0.8` |
| `I-ATOMICITY-001` | `TransferFundsScenarioTest` | `TASK-0.3`, `TASK-0.8` |
| `I-FRAUD-001` | `FraudCheckHelperTest`, `SlidingWindowRuleTest` | `TASK-0.3`, `TASK-0.8` |

---

## 2. Implementation Tasks

### Phase 1: Gradle & Modulith Dependencies Setup
- [x] `TASK-0.1` Configure Spring Modulith dependencies (`spring-modulith-starter-core`, `spring-modulith-starter-test`, `spring-modulith-docs`) in `build.gradle`.

### Phase 2: Core Module Reorganization (`br.com.wallet.core`)
- [x] `TASK-0.2` Structure `br.com.wallet.core.api` package:
  - Migrate use case contracts: `TransferFunds`, `DepositFunds`, `WithdrawFunds`, `GetBalance`, `ValidateLedger`, `ReplayWallet`.
  - Migrate published domain events: `MoneyReceivedEvent`, `MoneySentEvent`, `WalletCreatedEvent`.
  - Migrate published projection models: `AccountBalance`, `LedgerValidationResult`.
- [x] `TASK-0.3` Structure `br.com.wallet.core.internal` package:
  - `core.internal.domain`: `Account`, `LedgerEntry`, `LedgerType`, `HashUtil`.
  - `core.internal.service`: `TransferFundsService`, `DepositFundsService`, `WithdrawFundsService`, `BalanceService`, `LedgerService`, `CreateWalletService`, `ReplayWalletService`, `ValidateLedgerService`.
  - `core.internal.persistence`: `AccountDao`, `LedgerDao`, `OutboxDao`.
  - `core.internal.outbox`: `OutboxPublisher`, `OutboxRelay`.
  - `core.internal.guard`: `FraudCheckHelper` (interacting with `:fraud`).

### Phase 3: Infrastructure Adapters Reorganization (`br.com.wallet.infrastructure`)
- [x] `TASK-0.4` Fix typo `infrasctructure` $\rightarrow$ `infrastructure`.
- [x] `TASK-0.5` Migrate REST adapters into `br.com.wallet.infrastructure.rest`:
  - Controllers: `OperationsController`, `WalletController`, `LedgerController`.
  - DTOs, mappers, exception handlers (`RestExceptionHandler`).
  - Ensure controllers strictly inject and call `core.api` interfaces.
- [x] `TASK-0.6` Migrate Messaging adapters into `br.com.wallet.infrastructure.messaging`:
  - NATS JetStream consumers: `TransferConsumer`, `WithdrawConsumer`, `DepositConsumer`.
  - DLQ handler, command publisher.
  - Configuration: `NatsConfig`, `RedisConfig`, `OpenTelemetryConfiguration`.

### Phase 4: Verification & Convergence
- [x] `TASK-0.7` Implement `ModulithArchitectureTest`:
  - Test `verifyArchitecture()` enforcing zero illegal package coupling.
  - Test `writeDocumentation()` generating PlantUML component diagrams.
- [ ] `TASK-0.8` Run full test suite and verify 100% pass rate:
  - Unit tests across domain, service, and fraud modules.
  - Testcontainers integration tests (PostgreSQL, Redis, NATS).
- [ ] `TASK-0.9` Generate SDD Traceability Report for `SPEC-000`.

---

## 3. Convergence & Verification Checklist

- [ ] `ModulithArchitectureTest.verifyArchitecture()` passes cleanly.
- [ ] `./gradlew test` passes with zero failures or skipped critical tests.
- [ ] PlantUML architecture documentation generated under `build/spring-modulith-docs/`.
- [ ] Zero functional regressions in financial ledger, balance calculation, or anti-fraud evaluation.
