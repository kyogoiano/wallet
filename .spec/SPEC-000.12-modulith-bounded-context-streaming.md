# 📋 Specification: SPEC-000.12 — Modulith Ingress Decentralization & Intra-Core Event Alignment

- **Status**: 📝 **Draft (Rev. 2 — Pure Refactor Aligned with History 78 & Modulith Events)**
- **Author**: Antigravity Platform Architecture & Messaging Guild
- **Date**: 2026-10-02
- **Target Release / Milestone**: Wallet Service V4 — Phase 000.12
- **Bounded Context / Module**: Spring Modulith DAG (`ledger`, `fraud`, `savings`, `dlq`) & `:edge`
- **Spec Slicing Scope**: Max 250 lines (`I-SDD-006`). Ingress command decentralization, in-process Modulith event alignment, and removal of god dispatchers.

---

## 0. Pre-Flight History & Context Audit

- **Histories & Summaries Audited**:
  - [`.histories/history78.txt`](file:///.histories/history78.txt): Identified need for pure refactoring: remove bloated encrypted consumer abstraction, enforce durable handoff before ACK (`I-STREAM-006`), establish deterministic command ownership (`I-STREAM-008`), and align DLQ subjects to `dlq.commands.*`.
  - [`.histories/history77.txt`](file:///.histories/history77.txt): Formalized dual DLQ scopes (`CORE_COMMAND` vs `BOUNDED_CONTEXT`).
  - [`SPEC-000.9`](file:///.spec/SPEC-000.9-reactive-edge-gateway-and-ingress-resilience.md) & [`SPEC-000.10`](file:///.spec/SPEC-000.10-financial-security.md): Edge-to-Core inter-process boundary over NATS JetStream with envelope encryption.
  - [`SPEC-001`](file:///.spec/SPEC-001-smart-savings-automation.md): Established Spring Modulith `@ApplicationModuleListener` in `savings`.
- **Foundational Constraints (`constitution.md` & `capability-boundaries.md`)**:
  - `I-LEDGER-001` (Immutable ledger), `I-ATOMICITY-001` (Single tx boundary).
  - Spring Modulith Mantra: Capabilities own their domain logic and consumers; intra-process communication uses Modulith events; NATS bridges process boundaries.

---

## 1. Intent & Architectural Boundary

Wallet Core is a **Modular Monolith (Spring Modulith)** running inside a single OS process (`wallet-core`), while Edge Gateway runs in an independent ingress process (`wallet-edge`).

Currently, the messaging topology has two major architectural flaws:
1. **Monolithic Ingress God Class**: `CoreCommandConsumer` in `infrastructure` subscribes to all Edge commands (`commands.wallet.*`) and dispatches them via a giant `switch (type)` to ledger use cases.
2. **Inner Event Boomerang via NATS**: Even though `ledger`, `fraud`, and `savings` run in the **same JVM process**, some internal events (e.g. `TransferCompletedEvent` for `FraudGraphConsumer`, `FraudEvent` for `FraudConsumer`) are needlessly serialized, written to the Outbox table, sent to external NATS, and consumed back into the same JVM! Meanwhile, `SavingsEventListener` already uses native, high-performance Spring Modulith `@ApplicationModuleListener`.

**SPEC-000.12** is a **pure refactoring slice** that restores architectural purity:
- **NATS JetStream** is reserved strictly for **Inter-Process Boundaries**:
  - Edge $\rightarrow$ Core Ingress: `commands.ledger.transfer`, `commands.ledger.deposit`, `commands.ledger.withdraw`.
  - Core $\rightarrow$ Edge Status: `status.*` broadcasts.
  - Core $\rightarrow$ External Egress: Outbox publishing for external third-party consumers.
- **Intra-Core Domain Events** are unified under **Spring Modulith In-Process Events**:
  - `FraudGraphConsumer` and `FraudConsumer` migrate to `@ApplicationModuleListener`, eliminating network roundtrips, serialization churn, and external broker dependency for internal state projections.
- **Ingress Command Consumers** are decentralized into their owning capability:
  - Decompose `CoreCommandConsumer` into single-responsibility consumers in `br.com.wallet.ledger.internal.messaging.consumer.*`.

---

## 2. Target Messaging Topology

```text
       EXTERNAL INGRESS (Inter-Process via NATS JetStream)
                 Edge Gateway (wallet-edge)
                            │
               commands.ledger.{transfer,deposit,withdraw}
                            ▼
               ┌─────────────────────────┐
               │     WALLET_COMMANDS     │
               └────────────┬────────────┘
                            │
     ┌──────────────────────┼──────────────────────┐
     ▼                      ▼                      ▼
TransferCommandConsumer DepositCommandConsumer WithdrawCommandConsumer
(ledger.internal.messaging) (ledger.internal.messaging) (ledger.internal.messaging)
     │                      │                      │
     └──────────────────────┴──────────────────────┘
                            │
       INTRA-PROCESS DOMAIN EVENTS (Spring Modulith @ApplicationModuleListener)
                            │
                 TransferCompletedEvent / FraudEvent
                            │
               ┌────────────┴────────────┐
               ▼                         ▼
      FraudGraphListener        SavingsEventListener
  (fraud.internal.listener)   (savings.internal.listener)
```

---

## 3. Cross-Feature & Invariant Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Side Effect / Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`:edge` (Publisher)** | `NatsEdgeCommandPublisher` | Route mismatch with Core consumers | `I-STREAM-004` (Canonical route mapping) |
| **`ledger` (Core)** | Dedicated command consumers | Modulith boundary violations | `I-STREAM-002` (Consumers isolated in `ledger.internal`) |
| **`fraud`** | Migrate from NATS to Modulith | Missed projections on transfer events | `I-STREAM-001` (`@ApplicationModuleListener` transactional async) |
| **`infrastructure`** | Remove `CoreCommandConsumer` | Ingress command routing break | `I-STREAM-008` (1:1 CommandRoute to consumer) |
| **`dlq`** | DLQ subject standardization | Unrouted dead letter messages | `I-STREAM-006` (Durable handoff before ACK) |

---

## 4. Mathematical & System Invariants

- **`I-STREAM-001` (Intra-Core Modulith Event Invariant)**: Internal state projections and reactions between Spring Modulith modules within Wallet Core MUST communicate via in-process `@ApplicationModuleListener` events. Bouncing internal events through external NATS back into the same JVM is forbidden.
- **`I-STREAM-002` (Modulith Package Encapsulation)**: Message consumer classes MUST reside inside `<module>.internal.messaging` and depend only on their own capability use cases and public `@NamedInterface` APIs of other modules.
- **`I-STREAM-003` (Canonical Command Subject Standard)**:
  $$\text{Subject}(\text{cmd}) = \text{"commands."} + \text{Capability} + \text{"."} + \text{Action}$$
  Example: `commands.ledger.transfer`, `commands.ledger.deposit`, `commands.ledger.withdraw`.
- **`I-STREAM-004` (Deterministic CommandRoute Mapping)**: Edge Gateway maps `CommandType` to a typed `CommandRoute(capability, subject)` without dynamic string guesswork.
- **`I-STREAM-005` (Zero Monolithic Dispatcher)**: The system SHALL NOT contain a single central router class with dynamic branching across disparate financial business operations.
- **`I-STREAM-006` (Durable Handoff Dependency)**: Ingress command consumers MUST obey the durable-handoff-before-ACK contract governed by `SPEC-000.11` (`I-TDLQ-009`).
- **`I-STREAM-007` (Consumer Ownership Principle)**: A consumer MUST live in the bounded context that owns the processing capability.
- **`I-STREAM-008` (Deterministic Command Ownership)**: Every canonical command subject MUST have exactly one owning consumer within the active Wallet Core deployment.

---

## 5. Functional Requirements (MoSCoW Prioritized — `I-SDD-004`)

### 5.1 Pillar A: Edge/Core Command Ingress Decentralization [MUST]
- **`REQ-STRM-001 [MUST]`**: Define typed `CommandRoute` mapping each `CommandType` to its canonical capability subject:
  - `TRANSFER` $\rightarrow$ `commands.ledger.transfer`
  - `DEPOSIT` $\rightarrow$ `commands.ledger.deposit`
  - `WITHDRAW` $\rightarrow$ `commands.ledger.withdraw`
- **`REQ-STRM-002 [MUST]`**: Update `NatsEdgeCommandPublisher` to publish using `CommandRoute`.
- **`REQ-STRM-003 [MUST]`**: Implement single-responsibility command consumers in `br.com.wallet.ledger.internal.messaging.consumer`:
  - `TransferCommandConsumer`: binds to `commands.ledger.transfer` (durable name `ledger-transfer-consumer`).
  - `DepositCommandConsumer`: binds to `commands.ledger.deposit` (durable name `ledger-deposit-consumer`).
  - `WithdrawCommandConsumer`: binds to `commands.ledger.withdraw` (durable name `ledger-withdraw-consumer`).
- **`REQ-STRM-004 [MUST]`**: Delete the monolithic `CoreCommandConsumer` and stale classes in `br.com.wallet.infrastructure.messaging.consumer.business.*`.

### 5.2 Pillar B: Intra-Core Event Alignment (Modulith In-Process) [MUST]
- **`REQ-STRM-005 [MUST]`**: Migrate `FraudGraphConsumer` from NATS to `br.com.wallet.fraud.internal.listener.FraudGraphListener` annotated with `@ApplicationModuleListener`, reacting directly to `TransferCompletedEvent`.
- **`REQ-STRM-006 [MUST]`**: Migrate `FraudConsumer` timeline enrichment to `br.com.wallet.fraud.internal.listener.FraudEventListener` annotated with `@ApplicationModuleListener`, reacting directly to `FraudEvent`.
- **`REQ-STRM-007 [MUST]`**: Keep Outbox Relay publishing to NATS strictly for external consumer egress.
- **`REQ-STRM-008 [MUST]`**: Verify Modulith boundaries pass cleanly using `ApplicationModules.verify()`.

*(Note: DLQ, failure classification, and retry semantics are governed strictly by SPEC-000.11 and are not redefined here).*

### 5.3 Scope Fencing [WON'T]
- **`REQ-STRM-W01 [WON'T]`**: Introduce new cryptographic protocols or encrypt internal Modulith events.
- **`REQ-STRM-W02 [WON'T]`**: Split Wallet Core into distributed microservice containers (Spring Modulith remains in-process).
