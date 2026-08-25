# 🗺️ Wallet Service — Spec Kit & Feature Evolution Roadmap

> **Status**: Approved Foundation & Active Roadmap  
> **Methodology**: Spec-Driven Development (SDD) & GitHub Spec Kit  
> **Baseline Version**: Wallet Service V3 (Transactional Ledger + NATS JetStream + Anti-Fraud Engine)  
> **Target Vision**: Programmable Money & AI-Enabled Financial Platform  
> **Modularity Engine**: Spring Modulith (Application Modules)  
> **Architectural Mantra**: *"Capabilities observe, analyze, decide, and propose. Wallet Core authorizes and executes."*

---

## 🧭 Executive Summary & Architectural Vision

The **Wallet Service** has successfully established a high-integrity core foundation (V1–V3):
- **Core Ledger**: Append-only, cryptographically hash-chained (`SHA-256`), row-level locked (`SELECT FOR UPDATE`), tamper-evident accounting log.
- **Event Streaming**: Transactional Outbox Pattern with NATS JetStream command/event buses.
- **Fraud & Risk Engine**: Pre-execution gate with $O(1)$ Caffeine sliding windows, Redis distributed velocity counters, and async event enrichment.
- **Observability**: OpenTelemetry end-to-end distributed tracing with `operation_id` baggage propagation and OpenObserve OTLP exports.

### Current Codebase Anatomy (The Baseline)
- **Root Application (`:`)**: Spring Boot main application hosting domain use cases, DAOs, REST controllers, Outbox relay, and NATS JetStream consumers.
- **Subproject `:core`**: Shared domain primitives, tracing annotations (`Traceable`, `TracingAspect`), and exceptions.
- **Subproject `:fraud`**: Dedicated Anti-Fraud & Risk Scoring engine (Caffeine sliding window, Redis velocity rules).

### The Evolution: Refactor $\rightarrow$ Modulith Foundation $\rightarrow$ Programmable Capabilities

Before introducing new business capabilities (Smart Savings, Goal Engine, Subscription Intelligence, AI Copilot), we perform a non-breaking architecture alignment (**SPEC-000**) to organize the codebase into a verified **Spring Modulith baseline**.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     Wallet Monolith Application                         │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                            core                                   │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │ core.api (Published API: TransferFunds, GetBalance, Events) │  │  │
│  │  └──────────────────────────────┬──────────────────────────────┘  │  │
│  │  ┌──────────────────────────────┴──────────────────────────────┐  │  │
│  │  │ core.internal (Ledger, Accounts, Fraud Gate, Outbox Relay)  │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  └─────────────────────────────────┬─────────────────────────────────┘  │
│                                    │ Published API & Modulith Events    │
│        ┌───────────────────────────┼───────────────────────────┐        │
│        ▼                           ▼                           ▼        │
│  ┌───────────────┐           ┌───────────────┐           ┌───────────┐  │
│  │    savings    │           │     goals     │           │intel/subs │  │
│  │ (Modulith App)│           │ (Modulith App)│           │ (Modulith)│  │
│  └───────┬───────┘           └───────┬───────┘           └─────┬─────┘  │
│          │                           │                         │        │
│          └───────────────────────────┼─────────────────────────┘        │
│                                      ▼                                  │
│                            ┌───────────────────┐                        │
│                            │      copilot      │                        │
│                            │ (MCP Server Tool) │                        │
│                            └───────────────────┘                        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 📐 Conceptual Taxonomy: Capability ≠ Module ≠ Tool

| Concept | Dimension | Implementation Mechanism | Concrete Examples |
| :--- | :--- | :--- | :--- |
| **Capability** | **Business Domain** | Domain logic, financial rules, analysis, proposals. | Smart Savings, Goal Strategy Engine, Subscription Intelligence. |
| **Application Module** | **Modularity / Packaging** | **Spring Modulith Application Module** (package boundaries verified by `ApplicationModules.verify()`). | `br.com.wallet.savings`, `br.com.wallet.goals`. |
| **Tool** | **Interaction Interface** | External contract for users or AI agents. | MCP Tool (`mcp://`), REST Endpoint (`/api/v1/...`), CLI. |

---

## 🔄 Dual-Tier Event Architecture

```text
               IN-PROCESS (Domain Extensions)
       ┌──────────────────────────────────────────────┐
       │             Spring Modulith                  │
       │                                              │
       │ Core.api.WalletEvents ──> @ApplicationModuleListener
       │   • Core ──> savings                         │
       │   • Core ──> goals                           │
       │   • Core ──> intelligence                    │
       └──────────────────────────────────────────────┘

             OUT-OF-PROCESS (Durable & Distributed)
       ┌──────────────────────────────────────────────┐
       │     Transactional Outbox ──> NATS JetStream  │
       │                                              │
       │   • Cross-service integration                │
       │   • Distributed fraud projection enrichers   │
       │   • Audit & analytical pipelines             │
       └──────────────────────────────────────────────┘
```

---

## 🏛️ Core Architectural Invariants

| Invariant ID | Rule Statement | Enforcement Layer |
| :--- | :--- | :--- |
| **`I-MODULITH-001`** | **Internal Encapsulation**: No module outside `ledger` SHALL directly access types contained in `br.com.wallet.ledger.internal.*` or database DAOs directly. | Spring Modulith Architecture Verification (`ApplicationModules.verify()`) |
| **`I-MODULITH-002`** | **Published API Access**: Cross-module interactions with the ledger engine SHALL occur exclusively through `br.com.wallet.ledger.api.*`. | Spring Modulith Architecture Verification (`ApplicationModules.verify()`) |
| **`I-SAVINGS-001`** | **Non-Re-entrant Execution**: Savings sweeps dispatched to `ledger.api.TransferFundsUseCase` MUST carry an idempotent `operation_id` derived deterministically from triggering transaction to prevent loops. | `br.com.wallet.savings` Rule Engine |
| **`I-SAVINGS-002`** | **Zero Direct Ledger Mutation**: The `savings` module MUST NOT access `ledger.internal.*` or mutate `ledger`/`accounts` tables directly. | `br.com.wallet.savings` |
| **`I-AI-001`** | **Human-in-the-Loop for AI Writes**: AI agents MUST NOT unilaterally execute financial transactions without explicit user approval. | AI / MCP Gateway |
| **`I-AI-002`** | **AI Critical Path Isolation**: The AI integration layer SHALL remain strictly outside all synchronous monetary transaction critical paths. Core performance and correctness MUST NOT depend on AI inference or MCP transport. | Protocol Boundary |

---

## 📊 5-Phase SDD Initiative Breakdown

```mermaid
gantt
    title Wallet Service Evolution Roadmap
    dateFormat  YYYY-MM-DD
    section Phase 0: Baseline Refactor
    SPEC-000 Architecture Alignment     :done, p00_1, 2026-08-22, 2d
    TASKS-000 & Modulith Baseline       :done, p00_2, after p00_1, 2d
    section Phase 1: Programmable Money
    SPEC-001 Smart Savings Module       :active, p1_1, after p00_2, 3d
    TASKS-001 & TDD Implementation      :p1_2, after p1_1, 4d
    section Phase 2: Goal Engine
    SPEC-002 Financial Goal Engine      :p2_1, after p1_2, 3d
    TASKS-002 & Strategy Engine         :p2_2, after p2_1, 4d
    section Phase 3: Intelligence
    SPEC-003 Subscription & Spend Intel :p3_1, after p2_2, 3d
    TASKS-003 & Pattern Extraction      :p3_2, after p3_1, 4d
    section Phase 4: AI Copilot & MCP
    SPEC-004 MCP Server & AI Copilot    :p4_1, after p3_2, 3d
    TASKS-004 & Tool Protocol Delivery  :p4_2, after p4_1, 5d
```

---

### 🔹 Phase 0: Architecture Alignment & Modulith Core Baseline (Refactoring)
**Spec Identifier**: [`SPEC-000-architecture-alignment-modulith-baseline`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md)  
**Status**: 🟢 **Completed & Verified**  
**Core Abstraction**: `Architecture Refactoring & Baseline Verification`

- **Intent**: Refactor and align the existing codebase into a verified Spring Modulith baseline with `ledger.api` (published interface) and `ledger.internal` (sealed implementation), fixing package typos and ensuring zero regression on existing tests.
- **Spec Kit Artifacts**:
  - [`.spec/SPEC-000-architecture-alignment-modulith-baseline.md`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md)
  - [`.spec/PLAN-000-architecture-alignment-modulith-baseline.md`](file:///.spec/PLAN-000-architecture-alignment-modulith-baseline.md)
  - [`.spec/TASKS-000-architecture-alignment-modulith-baseline.md`](file:///.spec/TASKS-000-architecture-alignment-modulith-baseline.md)
  - [`.spec/summaries/SUMMARY-000-architecture-alignment-modulith-baseline.md`](file:///.spec/summaries/SUMMARY-000-architecture-alignment-modulith-baseline.md)

---

### 🔹 Phase 1: Smart Savings & Programmable Money
**Spec Identifier**: [`SPEC-001-smart-savings-automation`](file:///.spec/SPEC-001-smart-savings-automation.md)  
**Status**: 🟢 **Completed & Verified**  
**Core Abstraction**: `Savings Plan & Rules (br.com.wallet.savings)`

- **Intent**: First production business capability module reacting to `DepositCompletedEvent` and `TransferCompletedEvent` to trigger automated, deterministic savings actions via `ledger.api.TransferFundsUseCase`.
- **Capabilities Included**:
  - **Round-Up Rule**: Micro-savings sweeping transaction round-up deltas (e.g. R$ 47.30 $\rightarrow$ R$ 50.00 = R$ 2.70).
  - **Fixed Percentage Rule**: Automatic allocation of $X\%$ on incoming deposits.
  - **Balance Ceiling Sweep**: Automatic sweeping of funds exceeding target liquidity limits.
- **Spec Kit Artifacts**:
  - [`.spec/SPEC-001-smart-savings-automation.md`](file:///.spec/SPEC-001-smart-savings-automation.md) (Ratified)
  - [`.spec/PLAN-001-smart-savings-automation.md`](file:///.spec/PLAN-001-smart-savings-automation.md) (Approved)
  - [`.spec/TASKS-001-smart-savings-automation.md`](file:///.spec/TASKS-001-smart-savings-automation.md) (Completed)
  - [`.spec/summaries/SUMMARY-001-smart-savings-automation.md`](file:///.spec/summaries/SUMMARY-001-smart-savings-automation.md) (Verified)

---

### 🔹 Phase 1.1: Account Lifecycle State & Persistent Fraud Blocking
**Spec Identifier**: [`SPEC-001.1-account-lifecycle-state-and-fraud-blocking`](file:///.spec/SPEC-001.1-account-lifecycle-state-and-fraud-blocking.md)  
**Status**: 🟢 **Completed & Verified**  
**Core Abstraction**: `Account Lifecycle & Dual-Store Sync (ledger.api / fraud)`

- **Intent**: Close the architectural gap where fraud detection decisions only rejected transient operations without mutating the underlying account state in PostgreSQL.
- **Capabilities Included**:
  - **Account Status**: `ACTIVE`, `BLOCKED`, `SUSPENDED`, `FROZEN` on `accounts` table.
  - **AccountBlockedException**: Shared foundation exception in `core.exceptions`.
  - **Pre-Execution Gate**: Mandatory status check during `SELECT FOR UPDATE` queries.
  - **Dual-Store Sync**: Automated PostgreSQL $\leftrightarrow$ Redis user block state synchronization.
  - **Administrative Management**: `AccountStateUseCase` (`blockAccount`, `unblockAccount`, `getAccountStatus`).
- **Spec Kit Artifacts**:
  - [`.spec/SPEC-001.1-account-lifecycle-state-and-fraud-blocking.md`](file:///.spec/SPEC-001.1-account-lifecycle-state-and-fraud-blocking.md) (Ratified)
  - [`.spec/PLAN-001.1-account-lifecycle-state-and-fraud-blocking.md`](file:///.spec/PLAN-001.1-account-lifecycle-state-and-fraud-blocking.md) (Approved)
  - [`.spec/TASKS-001.1-account-lifecycle-state-and-fraud-blocking.md`](file:///.spec/TASKS-001.1-account-lifecycle-state-and-fraud-blocking.md) (Completed)
  - [`.spec/summaries/SUMMARY-001.1-account-lifecycle-state-and-fraud-blocking.md`](file:///.spec/summaries/SUMMARY-001.1-account-lifecycle-state-and-fraud-blocking.md) (Verified)

---

### 🔹 Phase 2: Financial Goal & Cashflow Strategy Engine
**Spec Identifier**: `SPEC-002-financial-goal-engine`  
**Status**: ⚪ Planned  
**Core Abstraction**: `Goals & Strategy (br.com.wallet.goals)`

- **Intent**: Goal-oriented financial strategy calculation utilizing `spring-modulith-moments` (`MonthHasPassed`, `DayHasPassed`).
- **Spec Kit Artifacts**:
  - `.spec/SPEC-002-financial-goal-engine.md`

---

### 🔹 Phase 3: Subscription & Spending Intelligence
**Spec Identifier**: `SPEC-003-spending-and-subscription-intelligence`  
**Status**: ⚪ Planned  
**Core Abstraction**: `Intelligence (br.com.wallet.intelligence)`

- **Intent**: Extract recurring financial patterns, predict upcoming obligations, and alert on fee/rate anomalies without state mutation.
- **Spec Kit Artifacts**:
  - `.spec/SPEC-003-spending-and-subscription-intelligence.md`

---

### 🔹 Phase 4: AI Financial Copilot & Model Context Protocol (MCP)
**Spec Identifier**: `SPEC-004-ai-financial-copilot-mcp`  
**Status**: ⚪ Planned  
**Core Abstraction**: `Agent & Tools (br.com.wallet.copilot)`

- **Intent**: Expose wallet capabilities to AI agents via standardized Model Context Protocol (MCP) with human-in-the-loop approvals.
- **Spec Kit Artifacts**:
  - `.spec/SPEC-004-ai-financial-copilot-mcp.md`

---

## 🛠️ SDD Execution Governance

```text
[1. SPECIFY] ──> [2. CLARIFY] ──> [3. PLAN] ──> [4. TASKS] ──> [5. ANALYZE] ──> [6. IMPLEMENT (TDD)] ──> [7. CONVERGE]
```

1. **Gate 1 (Ratification)**: Spec authoring with formal mathematical invariants $\rightarrow$ Human alignment.
2. **Gate 2 (Architecture Sign-off)**: Plan with ADRs, sequence diagrams, failure modes, and threat modeling (`/threat-model`).
3. **Gate 3 (Pre-Implementation Check)**: Tasks breakdown mapped 1-to-1 to requirement IDs (`REQ-XXX`) and invariants (`I-XXX`).
4. **Gate 4 (Convergence Gate)**: Full test suite (`./gradlew test jacocoTestReport`), Modulith verification (`ModulithArchitectureTest`), Zero Spec-Drift reconciliation, and execution summary.
