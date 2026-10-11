# 📋 Specification: SPEC-000.12 — Intra-Core Bounded Context Event Alignment & Modulith Streaming

- **Status**: 📝 **Draft (Rev. 7 — Aligned with AbstractCommandsConsumer Bounded Contexts)**
- **Author**: Antigravity Platform Architecture & Messaging Guild
- **Date**: 2026-10-06
- **Target Release / Milestone**: Wallet Service V4 — Phase 000.12 (Pure Refactor)
- **Bounded Context / Module**: Spring Modulith DAG (`ledger`, `fraud`, `savings`)
- **Spec Slicing Scope**: Max 250 lines (`I-SDD-006`). Strictly bounded to intra-Core domain event streaming, bounded context command listeners, PostgreSQL Event Publication Registry, and decommissioning internal NATS event/command boomerangs. Edge Gateway, CoreCommandConsumer, DLQ, and Outbox Relay are strictly out of scope / untouched.

---

## 0. Pre-Flight History & Context Audit

- **Histories & Summaries Audited**:
  - [`.histories/history83.txt`](file:///.histories/history83.txt), [`.histories/history82.txt`](file:///.histories/history82.txt), [`.histories/history80.txt`](file:///.histories/history80.txt): Established intra-Core boundary demarcation, Spring Modulith Event Publication Registry durability, and removal of internal event DLQ.
  - **`AbstractCommandsConsumer` Extensions Audit**: All subclasses (`TransferCommandConsumer`, `DepositCommandConsumer`, `WithdrawCommandConsumer`, `CreateWalletCommandConsumer`) represent intra-Core bounded context commands executed in the same process.
  - [`SPEC-000.11`](file:///.spec/SPEC-000.11-core-command-consumer-and-dlq-resilience.md): Governs Edge $\to$ Core command ingress (`commands.wallet.*`), command DLQ, and operator replay (fully completed and untouched).
- **Foundational Constraints (`constitution.md` & `capability-boundaries.md`)**:
  - Same JVM $\to$ Spring Modulith in-process events; Different process $\to$ NATS JetStream.
  - `I-LEDGER-001` (Immutable ledger), `I-ATOMICITY-001` (Single tx boundary).

---

## 1. Intent & Architectural Boundary

Wallet Core is a **Modular Monolith (Spring Modulith)** operating entirely within a single JVM process (`wallet-core`).
Currently, intra-Core communication between bounded contexts suffers from two **internal NATS boomerang anti-patterns**:
1. **Intra-Core Domain Events**: When `ledger` completes a transaction (`TransferCompletedEvent`) or `fraud` records an alert (`FraudEvent`), these events were published to NATS JetStream (`events.*`) and consumed by `FraudGraphConsumer` and `FraudConsumer` in the same JVM.
2. **Intra-Core Bounded Context Commands**: REST endpoints (`OperationsController`, `WalletController`) published commands (`commands.transfer`, `commands.deposit`, `commands.withdraw`, `commands.wallet`) to NATS JetStream via `NatsCommandPublisher`. Inside the same JVM, extensions of `AbstractCommandsConsumer` (`TransferCommandConsumer`, `DepositCommandConsumer`, `WithdrawCommandConsumer`, `CreateWalletCommandConsumer`) subscribed to NATS, and on failure routed to internal NATS DLQs (`commands.dlq.*`).

**SPEC-000.12** is a **strictly scoped pure refactor** to eliminate all intra-Core NATS hops:
- **Intra-Core Domain Events & Commands** are unified under **Spring Modulith In-Process Streaming**:
  - `FraudGraphListener` and `FraudEventListener` handle domain projections via `@ApplicationModuleListener`.
  - `TransferCommandListener`, `DepositCommandListener`, `WithdrawCommandListener`, and `CreateWalletCommandListener` handle bounded context commands via `@ApplicationModuleListener`, replacing all extensions of `AbstractCommandsConsumer`.
  - `OperationsController` and `WalletController` publish bounded context commands directly via `ApplicationEventPublisher`.
  - Durability and crash recovery are provided by Spring Modulith's **Event Publication Registry**, backed by PostgreSQL (`event_publication` table).
  - **The DLQ Exception (Registry $\neq$ DLQ)**: Internal NATS DLQs (`commands.dlq.transfer`, etc.) are **explicitly removed**. Spring Modulith's Event Publication Registry eliminates internal DLQ broker topics by persisting incomplete publications in PostgreSQL so they remain recoverable and eligible for republication.
  - **Configuration is Key**: Technical configuration is governed by [`durable-modulith-events`](file:///.agents/skills/durable-modulith-events/SKILL.md).
- **Dual Durability Demarcation (Registry $\neq$ Outbox)**:
  - Event Publication Registry guarantees **intra-Core event/command delivery**.
  - Outbox guarantees **external process/system egress**. `OutboxRelayWorker` remains untouched.
- **Strictly Out of Scope**: Edge Gateway, `commands.wallet.*`, `CoreCommandConsumer`, and external command DLQ are fully completed and remain completely untouched.

---

## 2. Target Messaging Topology

```text
       EXTERNAL INGRESS (SPEC-000.11 - Completed & Untouched)
             wallet-edge ──NATS──► CoreCommandConsumer
                                          │
    ══════════════════════════════════════╪═════════════════════════════════════
       INTRA-CORE BOUNDED CONTEXT STREAMING (SPEC-000.12 - Spring Modulith)
          OperationsController / WalletController
                           │ ApplicationEventPublisher
                           ▼
          Spring Modulith Event Publication Registry (PostgreSQL event_publication)
                           │
         ┌─────────────────┼─────────────────────────┐
         ▼                 ▼                         ▼
   TransferCommand    DepositCommand         WithdrawCommand / CreateWallet
         │                 │                         │
         ▼                 ▼                         ▼
   TransferFundsUseCase DepositFundsUseCase  WithdrawFundsUseCase / CreateWalletUseCase
         │
         ├── Ledger Transaction Boundary
         │        ├── In-Tx Event Publication Registry (event_publication)
         │        │         ├── TransferCompletedEvent ──► FraudGraphListener / SavingsEventListener
         │        │         └── FraudEvent ──────────────► FraudEventListener
         │        └── Outbox Table (PostgreSQL) ──► OutboxRelayWorker ──► NATS events.* (External)
```

---

## 3. Cross-Feature Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Side Effect / Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`ledger`** | `OperationsController`, `WalletController`, Use Cases | In-process dispatch & tx registration | `I-STREAM-001`, `I-STREAM-009` (Registry durability) |
| **`fraud`** | `FraudGraphListener`, `FraudEventListener` | Missed projections or duplicate execution | `I-STREAM-002`, `I-STREAM-003` (Registry + Idempotency) |
| **`savings`** | `SavingsEventListener` | Concurrency / lifecycle conflict | `I-STREAM-001` (Preserve existing Modulith behavior) |
| **`infrastructure`** | Host `FraudGraphListener` & `FraudEventListener`, decommission `AbstractCommandsConsumer` & NATS consumers | Inactive legacy subscriptions / cyclic coupling | `I-STREAM-002`, `I-STREAM-004` (Acyclic DAG `infrastructure -> ledger -> fraud`) |
| **`edge` / `dlq` / `outbox`** | **ZERO IMPACT (Out of Scope)** | Unintended scope creep | `REQ-STRM-W01` to `REQ-STRM-W03` (Strictly untouched) |

---

## 4. Mathematical & System Invariants

- **`I-STREAM-001` (Intra-Core Event Transport & Durability)**: Domain events emitted within Wallet Core MUST be delivered to interested Wallet Core modules through Spring Modulith in-process events (`@ApplicationModuleListener`). Such events MUST NOT be routed through external NATS and consumed again by another module in the same JVM. Event durability and recovery inside Core MUST be backed by Spring Modulith's Event Publication Registry in PostgreSQL.
- **`I-STREAM-002` (Modulith Package Encapsulation)**: Internal event listeners MUST reside inside the owning module's `internal.listener` package and MUST NOT be exposed as public module API.
- **`I-STREAM-003` (At-Least-Once Delivery & Idempotent Listener Contract)**: Intra-Core delivery semantics are at-least-once; exactly-once processing MUST NOT be assumed. Internal listeners MUST be idempotent based on canonical event identity (`eventId` / `operation_id`).
- **`I-STREAM-004` (Zero Internal Event Boomerang)**: Internal state projections and reactions between Bounded Contexts SHALL NOT depend on NATS JetStream or Outbox Relay.
- **`I-STREAM-005` (Behavioral Preservation — Pure Refactor Invariant)**: No intentional behavioral change is introduced. Existing externally observable business semantics MUST be preserved.
- **`I-STREAM-006` (Spring Modulith Evolution Clause)**: Spring Modulith 2.2.0-M2 is the development baseline, revalidated before final Wallet V4 release.
- **`I-STREAM-007` (No Internal Event DLQ — Registry $\neq$ DLQ)**: The Spring Modulith Event Publication Registry is the authoritative recovery mechanism for intra-Core event publication. No NATS-based internal event DLQ SHALL be introduced.
- **`I-STREAM-008` (Dual Durability Mechanism Demarcation — Registry $\neq$ Outbox)**: The Modulith Event Publication Registry and the external Outbox are independent durability mechanisms serving different delivery boundaries. Outbox Relay is out of scope and MUST remain untouched.
- **`I-STREAM-009` (Bounded Context Commands In-Process Execution)**: All bounded context commands previously handled by extensions of `AbstractCommandsConsumer` (`Transfer`, `Deposit`, `Withdraw`, `Wallet`) MUST execute as in-process Spring Modulith events with Event Publication Registry durability, eliminating intra-Core NATS command topics (`commands.transfer`, etc.) and internal NATS DLQ topics (`commands.dlq.*`).

---

## 5. Functional Requirements (MoSCoW Prioritized — `I-SDD-004`)

### 5.1 Intra-Core Event Alignment & Event Publication Registry [MUST]
- **`REQ-STRM-001 [MUST]`**: Migrate `FraudGraphConsumer` from NATS to `br.com.wallet.infrastructure.internal.listener.FraudGraphListener` annotated with `@ApplicationModuleListener`, reacting directly to `TransferCompletedEvent`.
- **`REQ-STRM-002 [MUST]`**: Migrate `FraudConsumer` timeline enrichment to `br.com.wallet.infrastructure.internal.listener.FraudEventListener` annotated with `@ApplicationModuleListener`, reacting directly to `FraudEvent`.
- **`REQ-STRM-003 [MUST]`**: Decommission and delete NATS consumers `FraudGraphConsumer` and `FraudConsumer` from `br.com.wallet.infrastructure.messaging.consumer`.
- **`REQ-STRM-004 [MUST]`**: Configure Spring Modulith Event Publication Registry with PostgreSQL persistence (`event_publication` table) following [`durable-modulith-events`](file:///.agents/skills/durable-modulith-events/SKILL.md), registering publication within the publishing transaction and completing upon listener success.
- **`REQ-STRM-005 [MUST]`**: Enforce idempotent processing in internal listeners based on canonical event identity (`eventId` / `operation_id`) to guarantee safe at-least-once republication/recovery.
- **`REQ-STRM-006 [MUST]`**: Persistent listener failures MUST remain observable as incomplete event publications in `event_publication` exposing sufficient metadata for operational diagnosis, without introducing an internal DLQ.
- **`REQ-STRM-007 [MUST]`**: Verify Modulith boundary integrity passes cleanly via `ApplicationModules.verify()`.
- **`REQ-STRM-008 [MUST]`**: Verify the absence of internal bounded context event DLQ mechanisms (`events.*` / `dlq.events.*`); event durability and republication/recovery are governed exclusively by the PostgreSQL Event Publication Registry.
- **`REQ-STRM-009 [MUST]`**: Migrate all extensions of `AbstractCommandsConsumer` (`TransferCommandConsumer`, `DepositCommandConsumer`, `WithdrawCommandConsumer`, `CreateWalletCommandConsumer`) to in-process Spring Modulith listeners (`@ApplicationModuleListener`) in `br.com.wallet.ledger.internal.listener`.
- **`REQ-STRM-010 [MUST]`**: Update `OperationsController` and `WalletController` to publish bounded context commands (`Transfer`, `Deposit`, `Withdraw`, `Wallet`) in-process via `ApplicationEventPublisher`.
- **`REQ-STRM-011 [MUST]`**: Decommission `AbstractCommandsConsumer` and delete legacy consumers `TransferCommandConsumer`, `DepositCommandConsumer`, `WithdrawCommandConsumer`, and `CreateWalletCommandConsumer`, eliminating internal command topics (`commands.transfer`, `commands.deposit`, `commands.withdraw`, `commands.wallet`) and internal DLQ topics (`commands.dlq.transfer`, etc.).

### 5.2 Scope Fencing [WON'T]
- **`REQ-STRM-W01 [WON'T]`**: Modify Edge Gateway, `commands.wallet.*` subjects, or `CoreCommandConsumer` (governed by `SPEC-000.11`).
- **`REQ-STRM-W02 [WON'T]`**: Modify `OutboxRelayWorker` or the Outbox external egress pipeline (remains untouched).
- **`REQ-STRM-W03 [WON'T]`**: Redesign command DLQ for Edge ingress (governed by `SPEC-000.11`).
- **`REQ-STRM-W04 [WON'T]`**: Introduce encryption for internal in-process Modulith events.
- **`REQ-STRM-W05 [WON'T]`**: Split Wallet Core into distributed microservice processes.
