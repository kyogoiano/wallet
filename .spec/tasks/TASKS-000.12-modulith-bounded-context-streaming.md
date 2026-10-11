# 📝 Task Breakdown: TASKS-000.12 — Intra-Core Bounded Context Event Alignment & Modulith Streaming

- **Associated Spec**: [`../SPEC-000.12-modulith-bounded-context-streaming.md`](file:///.spec/SPEC-000.12-modulith-bounded-context-streaming.md)
- **Associated Plan**: [`../plans/PLAN-000.12-modulith-bounded-context-streaming.md`](file:///.spec/plans/PLAN-000.12-modulith-bounded-context-streaming.md)
- **Status**: 📝 **Draft (Rev. 7 — Aligned with AbstractCommandsConsumer Bounded Contexts)**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-STRM-001` (FraudGraph Modulith Listener)| `[MUST]`| `FraudGraphListenerIT.shouldProjectGraphOnTransferCompletedEvent()` | `TASK-2.1`, `TASK-2.3` |
| `REQ-STRM-002` (FraudEvent Modulith Listener)| `[MUST]`| `FraudEventListenerIT.shouldEnrichTimelineOnFraudEvent()` | `TASK-3.1`, `TASK-3.3` |
| `REQ-STRM-003` (Decommission NATS Consumers)| `[MUST]`| `ModulithArchitectureTest.verifyArchitecture()` | `TASK-4.1` |
| `REQ-STRM-004` (Event Publication Registry)| `[MUST]`| `EventPublicationRegistryIT.shouldPersistAndCompleteEventsInPostgres()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-STRM-005` (Idempotent Listeners) | `[MUST]` | `FraudGraphListenerIT.shouldIgnoreDuplicateEventId()`<br/>`FraudEventListenerIT.shouldIgnoreDuplicateEventId()` | `TASK-2.2`<br/>`TASK-3.2` |
| `REQ-STRM-006` (Failure Observability) | `[MUST]` | `EventPublicationRecoveryIT.shouldKeepIncompletePublicationOnListenerFailure()` | `TASK-1.3` |
| `REQ-STRM-007` (Modulith Verification) | `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-6.1` |
| `REQ-STRM-008` (Verify Internal DLQ Absence)| `[MUST]`| `ModulithArchitectureTest.verifyArchitecture()` | `TASK-4.3` |
| `REQ-STRM-009` (Command Modulith Listeners) | `[MUST]` | `LedgerCommandListenersIT` | `TASK-5.1`, `TASK-5.2` |
| `REQ-STRM-010` (REST Ingress In-Process Pub) | `[MUST]` | `OperationsControllerTest`<br/>`WalletControllerTest` | `TASK-5.3` |
| `REQ-STRM-011` (Decommission AbstractCommandsConsumer)| `[MUST]`| `ModulithArchitectureTest.verifyArchitecture()` | `TASK-5.4` |
| `I-STREAM-001` (Intra-Core Event Transport)| `[MUST]` | `EventPublicationRegistryIT`<br/>`EventPublicationRecoveryIT` | `TASK-1.1`, `TASK-1.4` |
| `I-STREAM-002` (Modulith Encapsulation) | `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-6.1` |
| `I-STREAM-003` (At-Least-Once & Idempotency)| `[MUST]`| `EventPublicationRecoveryIT.shouldHandleDuplicateReplayIdempotently()` | `TASK-1.4`, `TASK-2.2`, `TASK-3.2` |
| `I-STREAM-004` (Zero Internal Boomerang) | `[MUST]` | `NoInternalEventNatsDependencyTest.verifyNoNatsDependencies()` | `TASK-4.1`, `TASK-4.2` |
| `I-STREAM-005` (Behavioral Preservation) | `[MUST]` | Full regression test suite verification | `TASK-6.2` |
| `I-STREAM-006` (Spring Modulith Evolution) | `[MUST]` | Configuration and architecture audit | `TASK-1.2`, `TASK-6.3` |
| `I-STREAM-007` (Registry != DLQ) | `[MUST]` | `EventPublicationRecoveryIT`<br/>Absence audit | `TASK-1.3`, `TASK-4.3` |
| `I-STREAM-008` (Registry != Outbox / Untouched)| `[MUST]`| `ExternalOutboxIsolationIT.shouldIsolateRegistryFromOutbox()` | `TASK-4.4` |
| `I-STREAM-009` (Commands In-Process Execution)| `[MUST]`| `LedgerCommandListenersIT` | `TASK-5.1`, `TASK-5.2` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing an atomic task, isolate working focus to the target card. Never load unrelated module files into memory.

```markdown
### 🎯 Active Task Card: TASK-X.Y
- **Target Invariant**: I-STREAM-00X
- **Target Requirement**: REQ-STRM-00X [MUST]
- **Target Files**: <TargetClass>.java, <TargetClassTest>.java
- **In-Scope Contracts**: Inputs -> TargetEvent, Outputs -> ExpectedProjection
- **Forbidden Boundary**: Do not touch Edge Gateway, CoreCommandConsumer, DLQ, or OutboxRelayWorker.
```

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Spring Modulith Event Publication Registry Configuration & Recovery ([MUST])
- [x] `TASK-1.1` [RED]: Write integration test in `EventPublicationRegistryIT` asserting that publishing a domain event (`TransferCompletedEvent`) within a transaction records an entry in the PostgreSQL `event_publication` table, and marks it complete once listeners finish (`REQ-STRM-004`, `I-STREAM-001`).
- [x] `TASK-1.2` [GREEN]: Configure Spring Modulith JDBC Event Publication Registry with PostgreSQL persistence (`event_publication` table) following [`durable-modulith-events`](file:///.agents/skills/durable-modulith-events/SKILL.md) and verify schema initialization (`REQ-STRM-004`, `I-STREAM-006`).
- [x] `TASK-1.3` [RED]: Write integration test in `EventPublicationRecoveryIT` asserting that persistent listener failures leave `completion_date` `NULL` in `event_publication`, exposing diagnostic metadata for operational recovery (`REQ-STRM-006`, `I-STREAM-007`).
- [x] `TASK-1.4` [RED/GREEN]: Write crash recovery and at-least-once duplicate delivery tests in `EventPublicationRecoveryIT` verifying that recovered incomplete publications are re-dispatched and that duplicate deliveries are handled idempotently without side-effects (`REQ-STRM-005`, `I-STREAM-001`, `I-STREAM-003`).

### Phase 2: FraudGraph In-Process Listener Migration ([MUST])
- [x] `TASK-2.1` [RED]: Write integration test in `FraudGraphListenerIT` verifying that `TransferCompletedEvent` published by `ledger` is received in-process by `FraudGraphListener` and updates relational graph projections without NATS (`REQ-STRM-001`, `I-STREAM-001`).
- [x] `TASK-2.2` [RED]: Write idempotency test in `FraudGraphListenerIT` verifying that duplicate events with the same canonical `eventId` (or `operation_id`) are safely ignored without corrupting graph state (`REQ-STRM-005`, `I-STREAM-003`).
- [x] `TASK-2.3` [GREEN]: Implement `FraudGraphListener` in `br.com.wallet.infrastructure.internal.listener` annotated with `@ApplicationModuleListener` and canonical event identity idempotency guard (`REQ-STRM-001`, `REQ-STRM-005`, `I-STREAM-003`).

### Phase 3: FraudEvent In-Process Listener Migration ([MUST])
- [x] `TASK-3.1` [RED]: Write integration test in `FraudEventListenerIT` verifying that `FraudEvent` published by `ledger` (`FraudCheckHelper`) is received in-process by `FraudEventListener` and enriches the timeline without NATS (`REQ-STRM-002`, `I-STREAM-001`).
- [x] `TASK-3.2` [RED]: Write idempotency test in `FraudEventListenerIT` verifying that duplicate events with the same canonical `eventId` (or `operation_id`) are safely ignored (`REQ-STRM-005`, `I-STREAM-003`).
- [x] `TASK-3.3` [GREEN]: Implement `FraudEventListener` in `br.com.wallet.infrastructure.internal.listener` annotated with `@ApplicationModuleListener` and canonical event identity idempotency guard (`REQ-STRM-002`, `REQ-STRM-005`, `I-STREAM-003`).

### Phase 4: Decommission Internal NATS Consumers & Verify Isolation ([MUST])
- [x] `TASK-4.1` [GREEN]: Delete `FraudGraphConsumer` and `FraudConsumer` from `br.com.wallet.infrastructure.messaging.consumer` (`REQ-STRM-003`, `I-STREAM-004`).
- [x] `TASK-4.2` [RED/GREEN]: Write architectural negative test `NoInternalEventNatsDependencyTest` asserting that internal listeners (`FraudGraphListener`, `FraudEventListener`, `SavingsEventListener`) have zero dependencies on NATS broker classes (`I-STREAM-004`).
- [x] `TASK-4.3` [GREEN]: Verify the absence of internal event DLQ topics/shims (`dlq.events.*`), asserting that internal bounded context events rely solely on the PostgreSQL Event Publication Registry (`REQ-STRM-008`, `I-STREAM-007`).
- [x] `TASK-4.4` [RED/GREEN]: Write integration test `ExternalOutboxIsolationIT` asserting that `OutboxRelayWorker` and the outbox pipeline remain completely isolated from internal Modulith listener failures and continue publishing external egress uninterrupted (`I-STREAM-008`, `REQ-STRM-W02`).

### Phase 5: Bounded Context Command Listeners Migration ([MUST])
- [x] `TASK-5.1` [RED]: Write integration test `LedgerCommandListenersIT` asserting that publishing `Transfer`, `Deposit`, `Withdraw`, and `Wallet` commands via `ApplicationEventPublisher` triggers the use cases and completes in PostgreSQL `event_publication` (`REQ-STRM-009`, `I-STREAM-009`).
- [x] `TASK-5.2` [GREEN]: Implement `TransferCommandListener`, `DepositCommandListener`, `WithdrawCommandListener`, and `CreateWalletCommandListener` in `br.com.wallet.ledger.internal.listener` annotated with `@ApplicationModuleListener` (`REQ-STRM-009`, `I-STREAM-009`).
- [x] `TASK-5.3` [RED/GREEN]: Update `OperationsController` and `WalletController` to publish bounded context commands via `ApplicationEventPublisher`, and update `OperationsControllerTest` and `WalletControllerTest` (`REQ-STRM-010`).
- [x] `TASK-5.4` [GREEN]: Decommission `AbstractCommandsConsumer` and delete legacy consumers `TransferCommandConsumer`, `DepositCommandConsumer`, `WithdrawCommandConsumer`, `CreateWalletCommandConsumer` from `br.com.wallet.infrastructure.messaging.consumer.business`, and remove `AbstractCommandsConsumerTest` (`REQ-STRM-011`).

### Phase 6: Architecture Verification, Behavioral Equivalence & Summary ([MUST])
- [ ] `TASK-6.1` [GREEN]: Run Spring Modulith verification test `ModulithArchitectureTest.verifyArchitecture()`, asserting zero architectural violations, strict module boundary encapsulation (`I-STREAM-002`), and clean DAG (`REQ-STRM-007`).
- [ ] `TASK-6.2` [GREEN]: Verify behavioral preservation (`I-STREAM-005`): ensure all existing ledger, fraud, savings, and edge integration tests remain green without regression.
- [ ] `TASK-6.3` [GREEN]: Bi-directional reconciliation audit (`I-SDD-003`): verify 100% congruence between `SPEC-000.12`, `PLAN-000.12`, `TASKS-000.12`, and implementation code. Author `SUMMARY-000.12-modulith-bounded-context-streaming.md` with Practical Verification Guide (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [ ] All unit tests pass: `./gradlew test`
- [ ] All integration tests pass: Testcontainers suite green
- [ ] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [x] Event Publication Registry table (`event_publication`) active and verified in PostgreSQL
- [x] Zero internal event boomerang: no intra-Core listeners subscribing to NATS `events.*`
- [x] Negative architectural test (`NoInternalEventNatsDependencyTest`) passes cleanly
- [x] Registry vs Outbox isolation verified (`ExternalOutboxIsolationIT`)
- [x] Edge Gateway, `commands.wallet.*`, `CoreCommandConsumer`, and `dlq` verified 100% untouched
- [x] OutboxRelayWorker verified 100% untouched
- [x] Practical Verification Guide with seed data and cURL/CLI commands authored in summary
