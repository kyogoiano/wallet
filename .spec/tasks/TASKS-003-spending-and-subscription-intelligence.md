# 📝 Task Breakdown: TASKS-003 — Spending & Subscription Intelligence (Phase 3.0 Module Foundation)

- **Associated Spec**: [`../SPEC-003-spending-and-subscription-intelligence.md`](file:///.spec/SPEC-003-spending-and-subscription-intelligence.md)
- **Associated Plan**: [`../plans/PLAN-003-spending-and-subscription-intelligence.md`](file:///.spec/plans/PLAN-003-spending-and-subscription-intelligence.md)
- **Status**: 📝 **Approved / Ready for Execution**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until all `[MUST]` criteria are green (`I-SDD-004`).
- **Atomic Spec Slicing**: Phase 3.0 covers strictly Module Foundation and Scaffolding. Recurrence algorithms (`SPEC-003.1`) and cashflow forecasting (`SPEC-003.2`) are strictly out of scope.

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test / Artifact | Task IDs |
| :--- | :---: | :--- | :--- |
| `REQ-INTEL-001` (Modulith Module Definition) | `[MUST]` | `package-info.java`, `ModulithArchitectureTest` | `TASK-3.0.1` |
| `REQ-INTEL-002` (Modulith Boundary Verification)| `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-3.0.2` |
| `REQ-INTEL-003` (In-Process Event Ingestion) | `[MUST]` | `SpendingEventListenerIT.shouldConsumeCoreEventsInProcess()` | `TASK-3.0.6` |
| `REQ-INTEL-004` (Event Idempotent Side Effects)| `[MUST]` | `SpendingEventListenerTest.shouldHandleEventIdempotently()`<br/>`SpendingEventListenerIT.shouldIgnoreRedeliveredEventId()` | `TASK-3.0.7`<br/>`TASK-3.0.8` |
| `REQ-INTEL-005` (Tenant Validation & Propagation)| `[MUST]`| `SpendingEventListenerTest.shouldRejectMissingTenantId()`<br/>`TenantIsolationArchitectureTest` | `TASK-3.0.4`<br/>`TASK-3.0.5`<br/>`TASK-3.0.11` |
| `REQ-INTEL-006` (Foundation Schema Conventions) | `[MUST]` | `docker/init/schema.sql` migration conventions | `TASK-3.0.3` |
| `REQ-INTEL-007` (Architectural Triads) | `[MUST]` | `NoInternalEventNatsDependencyTest`<br/>`ZeroLedgerMutationTest`<br/>`TenantIsolationArchitectureTest` | `TASK-3.0.9`<br/>`TASK-3.0.10`<br/>`TASK-3.0.11` |
| `REQ-INTEL-008` (Decision Algebra Integration Seam)| `[SHOULD]`| `DecisionSeamIsolationTest` | `TASK-3.0.12` |
| `I-INTEL-001` (Zero Ledger Mutation) | `[MUST]` | `ZeroLedgerMutationTest` | `TASK-3.0.10` |
| `I-INTEL-002` (In-Process Modulith Ingestion) | `[MUST]` | `SpendingEventListenerIT`, `NoInternalEventNatsDependencyTest` | `TASK-3.0.6`, `TASK-3.0.9` |
| `I-INTEL-004` (Strict API Boundary vs Ledger) | `[MUST]` | `package-info.java`, `ModulithArchitectureTest` | `TASK-3.0.1`, `TASK-3.0.2` |
| `I-INTEL-005` (Hot-Path Airgap) | `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-3.0.1`, `TASK-3.0.2` |
| `I-INTEL-006` / `I-INTEL-007` (Failure Isolation) | `[SHOULD]` | `DecisionSeamIsolationTest` | `TASK-3.0.12` |
| `I-INTEL-008` (Idempotent Event Processing) | `[MUST]` | `SpendingEventListenerTest`, `SpendingEventListenerIT` | `TASK-3.0.7`, `TASK-3.0.8` |
| `I-INTEL-010` (Strict Tenant Isolation) | `[MUST]` | `TenantIsolationArchitectureTest`, `SpendingEventListenerTest` | `TASK-3.0.4`, `TASK-3.0.5`, `TASK-3.0.11` |
| `I-STREAM-001` (Intra-Core Event Streaming) | `[MUST]` | `SpendingEventListenerIT` | `TASK-3.0.1`, `TASK-3.0.6` |
| `I-SDD-007` (Zero Legacy Cruft in Greenfield) | `[MUST]` | `docker/init/schema.sql`, mandatory `tenantId` contracts | `TASK-3.0.3`, `TASK-3.0.5` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing an atomic task, isolate working focus to the target card. Never load unrelated module files into memory.

```markdown
### 🎯 Active Task Card: TASK-3.0.X
- **Target Invariant**: I-INTEL-00X, I-SEC-005
- **Target Requirement**: REQ-INTEL-00X [MUST]
- **Target Files**: <TargetClass>.java, <TargetClassTest>.java
- **In-Scope Contracts**: Inputs -> Financial Events, Outputs -> Validated Analytical Seam
- **Forbidden Boundary**: Do not implement recurrence clustering (003.1), cashflow forecasts (003.2), or concrete evaluators (000.8.1).
```

---

## 3. Implementation Tasks (TDD Order: Red $\to$ Green $\to$ Refactor)

### Phase 1: Modulith Module Definition & Schema Conventions ([MUST])
- [x] `TASK-3.0.1` [GREEN]: Create `br.com.wallet.intelligence` with `package-info.java` annotated with `@ApplicationModule(displayName = "Spending & Subscription Intelligence", allowedDependencies = {"ledger::api", "core::api", "goals::api"})` (`REQ-INTEL-001`, `I-INTEL-004`, `I-STREAM-001`).
- [x] `TASK-3.0.2` [GREEN]: Run and verify `ModulithArchitectureTest.verifyArchitecture()`, asserting zero architectural violations, clean acyclic DAG, and strict API access (`REQ-INTEL-002`, `I-INTEL-004`).
- [x] `TASK-3.0.3` [GREEN]: Define foundation migration conventions in `docker/init/schema.sql` establishing composite tenant indexes and mandatory `tenant_id VARCHAR(64) NOT NULL` standard for downstream intelligence capability tables (`REQ-INTEL-006`, `I-INTEL-010`, `I-SDD-007`).

### Phase 2: In-Process Event Ingestion & Tenant Validation ([MUST])
- [x] `TASK-3.0.4` [RED]: Write unit test in `SpendingEventListenerTest` asserting that incoming `TransferCompletedEvent` and `WithdrawCompletedEvent` missing a valid `tenantId` (null or blank) throw `TenantContextMissingException` (`REQ-INTEL-005`, `I-INTEL-010`).
- [x] `TASK-3.0.5` [GREEN]: Implement `SpendingEventListener` in `br.com.wallet.intelligence.internal.listener` with `tenantId` validation and immutable propagation (`REQ-INTEL-005`, `I-INTEL-010`).
- [x] `TASK-3.0.6` [RED/GREEN]: Write integration test in `SpendingEventListenerIT` asserting that `TransferCompletedEvent` and `WithdrawCompletedEvent` emitted by `ledger` are received in-process via `@ApplicationModuleListener` and complete in PostgreSQL `event_publication` (`REQ-INTEL-003`, `I-INTEL-002`, `I-STREAM-001`).

### Phase 3: Canonical Event Idempotency Handling ([MUST])
- [x] `TASK-3.0.7` [RED]: Write unit and integration tests in `SpendingEventListenerTest` and `SpendingEventListenerIT` asserting that duplicate delivery of an event with the same canonical `eventId` is an idempotent no-op without duplicate side effects (`REQ-INTEL-004`, `I-INTEL-008`).
- [x] `TASK-3.0.8` [GREEN]: Implement canonical `eventId`-based idempotent side-effect handling in `SpendingEventListener` (`REQ-INTEL-004`, `I-INTEL-008`).

### Phase 4: Architectural Triads & Verification Gates ([MUST])
- [x] `TASK-3.0.9` [RED/GREEN]: Extend `NoInternalEventNatsDependencyTest` with ArchUnit assertion verifying that classes in `br.com.wallet.intelligence..` have zero dependencies on NATS classes or Outbox Relay (`REQ-INTEL-007`, `I-INTEL-002`, `I-STREAM-004`).
- [x] `TASK-3.0.10` [RED/GREEN]: Implement `ZeroLedgerMutationTest` verifying that `br.com.wallet.intelligence..` performs zero mutations against `ledger` or `accounts` tables and has zero access to ledger internal persistence (`REQ-INTEL-007`, `I-INTEL-001`, `I-INTEL-004`).
- [x] `TASK-3.0.11` [RED/GREEN]: Implement `TenantIsolationArchitectureTest` verifying strict tenant boundary isolation: events and state for `tenant-A` never pollute or interact with `tenant-B` (`REQ-INTEL-007`, `REQ-INTEL-005`, `I-INTEL-010`).

### Phase 5: Decision Algebra Seam & Bi-directional Convergence ([SHOULD] / [MUST])
- [x] `TASK-3.0.12` [SHOULD]: Implement integration seam test `DecisionSeamIsolationTest` verifying that semantic decision algebra contracts from `SPEC-000.8.1` produce `DecisionUnavailable<T>` upon evaluator failure and remain strictly non-blocking (`REQ-INTEL-008`, `I-INTEL-006`, `I-INTEL-007`).
- [x] `TASK-3.0.13` [MUST]: Run full test suite (`./gradlew test`) and Modulith architecture verification. Execute bi-directional reconciliation audit (`I-SDD-003`), proving 100% congruence between `SPEC-003`, `PLAN-003`, `TASKS-003`, and codebase, and author `SUMMARY-003-spending-and-subscription-intelligence.md` with Practical Verification Guide (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [x] All unit tests pass: `./gradlew test`
- [x] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [x] In-process event streaming verified via Spring Modulith Event Publication Registry (`event_publication` table)
- [x] Negative architectural test (`NoInternalEventNatsDependencyTest`) passes cleanly
- [x] Zero ledger mutation test (`ZeroLedgerMutationTest`) passes cleanly
- [x] Multi-tenant isolation test (`TenantIsolationArchitectureTest`) passes cleanly
- [x] No premature recurrence algorithms or cashflow forecasting code present in Phase 3.0 foundation
