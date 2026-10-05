# 📝 Task Breakdown: TASKS-000.12 — Modulith Ingress Decentralization & Intra-Core Event Alignment

- **Associated Spec**: [`../SPEC-000.12-modulith-bounded-context-streaming.md`](file:///.spec/SPEC-000.12-modulith-bounded-context-streaming.md)
- **Associated Plan**: [`../plans/PLAN-000.12-modulith-bounded-context-streaming.md`](file:///.spec/plans/PLAN-000.12-modulith-bounded-context-streaming.md)
- **Status**: 📝 **Draft**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-STRM-001` (Typed CommandRoute) | `[MUST]` | `CommandRouteTest.shouldResolveCanonicalRouteForCommandType()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-STRM-002` (Publisher Route Alignment)| `[MUST]` | `NatsEdgeCommandPublisherTest.shouldPublishToCanonicalSubject()` | `TASK-1.3`, `TASK-1.4` |
| `REQ-STRM-003` (Decentralized Consumers) | `[MUST]` | `TransferCommandConsumerIT.shouldConsumeTransferCommand()`<br/>`DepositCommandConsumerIT.shouldConsumeDepositCommand()`<br/>`WithdrawCommandConsumerIT.shouldConsumeWithdrawCommand()` | `TASK-2.1`, `TASK-2.2`<br/>`TASK-2.3`, `TASK-2.4`<br/>`TASK-2.5`, `TASK-2.6` |
| `REQ-STRM-004` (Decommission God Consumer)| `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-STRM-005` (FraudGraph Modulith Listener)| `[MUST]`| `FraudGraphListenerIT.shouldProjectGraphOnTransferCompletedEvent()` | `TASK-4.1`, `TASK-4.2` |
| `REQ-STRM-006` (FraudEvent Modulith Listener)| `[MUST]`| `FraudEventListenerIT.shouldEnrichTimelineOnFraudEvent()` | `TASK-4.3`, `TASK-4.4` |
| `REQ-STRM-007` (External Outbox Egress) | `[MUST]` | `OutboxRelayWorkerTest.shouldPublishExternalEgressOnly()` | `TASK-4.5` |
| `REQ-STRM-008` (Modulith Verification) | `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-5.1` |
| `I-STREAM-001` (Intra-Core Modulith Events)| `[MUST]` | `IntraCoreEventIntegrationTest.shouldNotUseNatsForInternalEvents()` | `TASK-4.1`, `TASK-4.3` |
| `I-STREAM-002` (Modulith Encapsulation) | `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-5.1` |
| `I-STREAM-003` (Canonical Command Subject) | `[MUST]`| `CommandRouteTest.shouldMatchCanonicalSubjectFormat()` | `TASK-1.1` |
| `I-STREAM-004` (Deterministic Route Mapping)| `[MUST]`| `CommandRouteTest.shouldMapDeterministically()` | `TASK-1.1` |
| `I-STREAM-005` (Zero Monolithic Dispatcher)| `[MUST]`| `ModulithArchitectureTest.verifyArchitecture()` | `TASK-3.1` |
| `I-STREAM-006` (Durable Handoff Dependency) | `[MUST]`| `DecentralizedConsumerHandoffIT.shouldCommitToDlqBeforeAck()` | `TASK-2.1` |
| `I-STREAM-007` (Consumer Ownership) | `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-5.1` |
| `I-STREAM-008` (Deterministic Ownership) | `[MUST]` | `CommandConsumerTopologyTest.shouldHaveOneConsumerPerCommand()` | `TASK-2.7` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing an atomic task, isolate working focus to the target card. Never load unrelated module files into memory.

```markdown
### 🎯 Active Task Card: TASK-X.Y
- **Target Invariant**: I-STREAM-00X
- **Target Requirement**: REQ-STRM-00X [MUST]
- **Target Files**: <TargetClass>.java, <TargetClassTest>.java
- **In-Scope Contracts**: Inputs -> TargetDTO, Outputs -> ExpectedResult
- **Forbidden Boundary**: Do not modify unrelated modules.
```

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Pillar A — Typed Ingress Route & Publisher Alignment ([MUST])
- [ ] `TASK-1.1` [RED]: Write unit tests in `CommandRouteTest` verifying canonical subject mapping:
  - `TRANSFER` $\rightarrow$ `commands.ledger.transfer` (durable `ledger-transfer-consumer`)
  - `DEPOSIT` $\rightarrow$ `commands.ledger.deposit` (durable `ledger-deposit-consumer`)
  - `WITHDRAW` $\rightarrow$ `commands.ledger.withdraw` (durable `ledger-withdraw-consumer`)
  - Format adherence to `commands.<capability>.<action>` (`I-STREAM-003`, `I-STREAM-004`).
- [ ] `TASK-1.2` [GREEN]: Implement `CommandRoute` record in `br.com.wallet.edge.command`.
- [ ] `TASK-1.3` [RED]: Write unit test in `NatsEdgeCommandPublisherTest` verifying that publishing commands routes strictly to the canonical subject derived from `CommandRoute.forType(command.type())`.
- [ ] `TASK-1.4` [GREEN]: Update `NatsEdgeCommandPublisher` to use `CommandRoute` for subject resolution instead of dynamic string templates.

### Phase 2: Pillar A — Decentralized Ingress Consumers in Ledger ([MUST])
- [ ] `TASK-2.1` [RED]: Write integration test in `TransferCommandConsumerIT` verifying that `TransferCommandConsumer`:
  - Subscribes to `commands.ledger.transfer` with durable consumer name `ledger-transfer-consumer`.
  - Validates transport security headers (`tenant_id`, `principal_id`, `key_id`, `publisher_id`).
  - Unwraps `CryptoEnvelope` and dispatches to `TransferFundsUseCase`.
  - On failure, commits durable DLQ handoff before ACKing NATS (`I-STREAM-006`, `I-TDLQ-009`).
- [ ] `TASK-2.2` [GREEN]: Implement `TransferCommandConsumer` in `br.com.wallet.ledger.internal.messaging.consumer`.
- [ ] `TASK-2.3` [RED]: Write integration test in `DepositCommandConsumerIT` verifying consumption of `commands.ledger.deposit` and execution of `DepositFundsUseCase`.
- [ ] `TASK-2.4` [GREEN]: Implement `DepositCommandConsumer` in `br.com.wallet.ledger.internal.messaging.consumer`.
- [ ] `TASK-2.5` [RED]: Write integration test in `WithdrawCommandConsumerIT` verifying consumption of `commands.ledger.withdraw` and execution of `WithdrawFundsUseCase`.
- [ ] `TASK-2.6` [GREEN]: Implement `WithdrawCommandConsumer` in `br.com.wallet.ledger.internal.messaging.consumer`.
- [ ] `TASK-2.7` [RED/GREEN]: Implement `CommandConsumerTopologyTest` asserting that every canonical command subject has exactly one consumer (`I-STREAM-008`).

### Phase 3: Pillar A — Decommission God Consumer & Stale Consumers ([MUST])
- [ ] `TASK-3.1` [GREEN]: Delete `CoreCommandConsumer` from `br.com.wallet.infrastructure.messaging.consumer` (`I-STREAM-005`).
- [ ] `TASK-3.2` [GREEN]: Delete stale consumer classes in `br.com.wallet.infrastructure.messaging.consumer.business.*`.

### Phase 4: Pillar B — Intra-Core Event Alignment via Spring Modulith ([MUST])
- [ ] `TASK-4.1` [RED]: Write integration test in `FraudGraphListenerIT` verifying that `TransferCompletedEvent` published by `ledger` is received in-process by `FraudGraphListener` and updates graph relationships without NATS.
- [ ] `TASK-4.2` [GREEN]: Implement `FraudGraphListener` in `br.com.wallet.fraud.internal.listener` annotated with `@ApplicationModuleListener`. Decommission NATS `FraudGraphConsumer`.
- [ ] `TASK-4.3` [RED]: Write integration test in `FraudEventListenerIT` verifying that `FraudEvent` published by `fraud` is received in-process by `FraudEventListener` and enriches the timeline without NATS.
- [ ] `TASK-4.4` [GREEN]: Implement `FraudEventListener` in `br.com.wallet.fraud.internal.listener` annotated with `@ApplicationModuleListener`. Decommission NATS `FraudConsumer`.
- [ ] `TASK-4.5` [RED/GREEN]: Verify `OutboxRelayWorker` publishes to NATS JetStream strictly for external event streams (`events.*`), confirming that internal consumers no longer depend on Outbox relay for intra-process state.

### Phase 5: Architecture Verification & Equivalence Gate ([MUST])
- [ ] `TASK-5.1` [GREEN]: Run Spring Modulith verification test `ModulithArchitectureTest.verifyArchitecture()`, asserting zero architectural violations, strict module boundary encapsulation (`I-STREAM-002`), and zero god routers (`I-STREAM-005`).
- [ ] `TASK-5.2` [GREEN]: Bi-directional reconciliation audit (`I-SDD-003`): verify 100% congruence between `SPEC-000.12`, `PLAN-000.12`, `TASKS-000.12`, and implementation code. Author `SUMMARY-000.12-modulith-bounded-context-streaming.md` with Practical Verification Guide (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [ ] All unit tests pass: `./gradlew test`
- [ ] All integration tests pass: Testcontainers suite green
- [ ] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [ ] Zero duplicate NATS headers; canonical lowercase `snake_case` headers throughout
- [ ] Strict mandatory `@NonNull tenantId` without legacy `DEFAULT 'default'` or fallback shims (`I-SDD-007`)
- [ ] Practical Verification Guide with seed data and cURL/CLI commands authored in summary
