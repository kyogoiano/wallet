# 📝 Task Breakdown: TASKS-000 — Architecture Alignment & Modulith Core Baseline

- **Associated Spec**: [`SPEC-000-architecture-alignment-modulith-baseline.md`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md)
- **Associated Plan**: [`PLAN-000-architecture-alignment-modulith-baseline.md`](file:///.spec/PLAN-000-architecture-alignment-modulith-baseline.md)
- **Status**: Completed / Verified
- **Author**: Antigravity Financial Architecture Team

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-ALIGN-001` | `TransferFundsServiceTest`, `BalanceServiceTest` | `TASK-0.2`, `TASK-0.3` |
| `REQ-ALIGN-002` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-0.1`, `TASK-0.4` |
| `REQ-ALIGN-003` | `OperationsControllerTest`, `TransferConsumerTest` | `TASK-0.5`, `TASK-0.6` |
| `REQ-ALIGN-004` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-0.7` |
| `REQ-ALIGN-005` | Full Test Suite (`./gradlew test`) | `TASK-0.8` |
| `I-LEDGER-001` | `LedgerServicesTest` | `TASK-0.3`, `TASK-0.8` |
| `I-LEDGER-002` | `HashUtilTest`, `LedgerServicesTest` | `TASK-0.3`, `TASK-0.8` |
| `I-BALANCE-001` | `BalanceServiceTest`, `ReplayWalletServiceTest` | `TASK-0.3`, `TASK-0.8` |
| `I-ATOMICITY-001` | `TransferFundsServiceTest` | `TASK-0.3`, `TASK-0.8` |
| `I-FRAUD-001` | `FraudCheckHelperTest`, `SlidingWindowRuleTest` | `TASK-0.3`, `TASK-0.8` |

---

## 2. Implementation Tasks

### Phase 1: Gradle & Modulith Dependencies Setup
- [x] `TASK-0.1` Configure Spring Modulith dependencies (`spring-modulith-starter-core`, `spring-modulith-starter-test`, `spring-modulith-docs`) and JaCoCo in `build.gradle`.

### Phase 2: Core Domain Module Reorganization (`br.com.wallet.wallet`)
- [x] `TASK-0.2` Structure `br.com.wallet.wallet.api` package:
  - Migrate use case contracts: `TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`, `BalanceUseCase`, `ValidateLedgerUseCase`, `ReplayWalletUseCase`, `CreateWalletUseCase`.
  - Migrate published domain events: `TransferCompletedEvent`, `DepositCompletedEvent`, `WithdrawCompletedEvent`, `FraudEvent`, `EventPublisher`.
  - Migrate published models & guards: `AccountBalance`, `LedgerValidationResult`, `FraudCheckHelper`, `CommandEnvelope`.
- [x] `TASK-0.3` Structure `br.com.wallet.wallet.internal` package:
  - `wallet.internal.service`: `TransferFundsService`, `DepositFundsService`, `WithdrawFundsService`, `BalanceService`, `LedgerService`, `CreateWalletService`, `ReplayWalletService`, `ValidateLedgerService`.
  - `wallet.internal.persistence`: `AccountDao`, `LedgerDao`, `OutboxDao`, `WalletOperationsDao`.
  - `wallet.internal.outbox`: `OutboxRelay`, `OutboxEvent`, `OutboxStatus`.
  - `wallet.internal.operation`: `Operation`, `OperationStatus`.

### Phase 3: Infrastructure Adapters Reorganization (`br.com.wallet.infrastructure`)
- [x] `TASK-0.4` Fix typo `infrasctructure` $\rightarrow$ `infrastructure`.
- [x] `TASK-0.5` Migrate REST adapters into `br.com.wallet.infrastructure.rest`:
  - Controllers: `OperationsController`, `WalletController`.
  - DTOs, mappers, exception handlers (`ApiExceptionHandler`).
  - Ensure controllers strictly inject and call `wallet.api` interfaces.
- [x] `TASK-0.6` Migrate Messaging adapters into `br.com.wallet.infrastructure.messaging`:
  - NATS JetStream consumers: `TransferCommandConsumer`, `WithdrawCommandConsumer`, `DepositCommandConsumer`, `CreateWalletCommandConsumer`.
  - DLQ persistence (`DlqOperationsDao`), publisher, enrichers.
  - Configuration: `NatsConfig`, `RedisConfig`, `OpenTelemetryConfiguration`.

### Phase 4: Verification, Coverage & Convergence
- [x] `TASK-0.7` Implement `ModulithArchitectureTest`:
  - Test `verifyArchitecture()` enforcing zero illegal package coupling.
  - Test `generateDocumentation()` generating PlantUML component diagrams.
- [x] `TASK-0.8` Author isolated unit tests and run full test suite with JaCoCo:
  - Unit tests across `wallet` services, `fraud` rules, and `infrastructure`.
  - Testcontainers integration tests (PostgreSQL, Redis, NATS).
- [x] `TASK-0.9` Generate SDD Execution Summary in `.spec/summaries/SUMMARY-000-architecture-alignment-modulith-baseline.md`.

---

## 3. Convergence & Verification Checklist

- [x] `ModulithArchitectureTest.verifyArchitecture()` passes cleanly (0 violations, 0 cycles).
- [x] `./gradlew test` passes with zero failures or skipped critical tests.
- [x] PlantUML architecture documentation generated under `build/spring-modulith-docs/`.
- [x] JaCoCo coverage reports generated under `build/reports/jacoco/test/html/`.
- [x] Zero functional regressions in financial ledger, balance calculation, or anti-fraud evaluation.
