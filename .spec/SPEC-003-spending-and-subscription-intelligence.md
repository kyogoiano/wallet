# 📋 Specification: SPEC-003 — Spending & Subscription Intelligence

- **Status**: Ratified
- **Author**: Antigravity Intelligence & Financial Planning Team
- **Date**: 2026-10-07
- **Target Release / Milestone**: Wallet Service V4 — Phase 3.0 (Module Scaffolding & Foundation)
- **Bounded Context / Module**: `br.com.wallet.intelligence`
- **Spec Slicing Scope**: Macro Architecture & Module Foundation Slice ($\le 250$ lines, `I-SDD-006`)

---

## 0. Pre-Flight History & Context Audit

- **Histories & Summaries Audited**:
  - `.histories/history0.txt`: Visa Subscription Manager concept (detect $\to$ predict $\to$ alert).
  - `.histories/history1.txt`: Deterministic feature extraction engine mirroring Anti-Fraud architecture for proactive wellness.
  - `.histories/history9.txt`: Clean capability dependency: Intelligence infers committed cashflow to feed `goals.api.model.CashflowProfile`.
  - `.histories/history84.txt`, `history85.txt`, `history86.txt`: Decoupled 3-pillar architecture: Foundation (003.0), Pattern Engine (003.1), Forecasting (003.2), and Decision Algebra (`SPEC-000.8.1`).
  - `.histories/history87.txt`: Foundation purity audit: stripping premature domain entities (`Subscription`, `InferredCashflowProfile`, `RuleSubscriptionEvaluator`) from Phase 3.0 to maintain strict capability slicing.
- **Foundational Constraints (`constitution.md`)**:
  - `I-LEDGER-001`: Immutable append-only ledger. Intelligence must never modify or delete past entries.
  - `I-ACCOUNT-001`: Account lifecycle state authority.
  - `I-STREAM-001` & `I-STREAM-004`: In-process Spring Modulith event streaming; zero NATS boomerang for internal events.
  - `I-TYPED-001` & `I-TYPED-006`: Hot-path airgap and anti-coercion semantics for typed decisions.
  - `I-SDD-007` & `I-SEC-005`: Strict multi-tenancy and zero legacy cruft (`tenant_id VARCHAR(64) NOT NULL`).

---

## 1. Intent & Business Value

### 1.1 Problem Statement
Digital wallet platforms lack structured analytical observation capabilities to monitor user outflows, detect hidden commitments, and project forward liquidity. Furthermore, attempting to implement analytical intelligence inside the financial core introduces severe risks of transactional latency degradation and boundary leaks.

### 1.2 Solution Intent
Establish `br.com.wallet.intelligence` as an autonomous, asynchronous Spring Modulith capability module that:
1. Observes financial transactions in-process via durable event publications without degrading core banking throughput.
2. Establishes the architectural foundation, event ingestion pipeline, and idempotency guarantees required by downstream capability slices (`SPEC-003.1` and `SPEC-003.2`).
3. Enforces strict multi-tenant isolation (`tenantId`) across all analytical models and projections.
4. Maintains an absolute airgap between synchronous financial execution and nearline intelligence runtimes.

---

## 2. Three-Pillar Macro Architecture

```text
                     LEDGER DOMAIN EVENTS (In-Process)
                                     │
                                     ▼
                   ┌───────────────────────────────────┐
                   │ SPRING MODULITH                   │
                   │ Event Publication Registry        │
                   └─────────────────┬─────────────────┘
                                     │
                                     ▼
        ┌─────────────────────────────────────────────────────────┐
        │ PILLAR 1: DETERMINISTIC PATTERN ENGINE (SPEC-003.1)     │
        │ - Structurally owned and implemented by SPEC-003.1      │
        │ - Cadence (I-SUB-001: Weekly/Monthly/Annual)            │
        │ - Variance (CV_A) & Cycle Count (N >= 3)                │
        │ - Confidence Score (I-SUB-002) & Price Spikes           │
        │ - Domain entities: Subscription, Cadence, Status        │
        └────────────────────────────┬────────────────────────────┘
                                     │
                                     ▼
                           SubscriptionCandidate
                                     │
        ┌────────────────────────────┴────────────────────────────┐
        │ PILLAR 2: SEMANTIC DECISION LAYER (SPEC-000.8.1)        │
        │ - Structurally owned and implemented by SPEC-000.8.1    │
        │ - DecisionQuestion<SubscriptionClassification>          │
        │ - Outcome: DecisionAnswer<T> | DecisionUnavailable<T>   │
        │ - Evaluators: RuleEvaluator, ONNX, Ollama               │
        └────────────────────────────┬────────────────────────────┘
                                     │
                                     ▼
                    Enriched Analytical Domain State
                                     │
        ┌────────────────────────────┴────────────────────────────┐
        │ PILLAR 3: FORECASTING & GOALS SYNC (SPEC-003.2)         │
        │ - Structurally owned and implemented by SPEC-003.2      │
        │ - Forward Liability Calendar (L7, L14, L30)             │
        │ - InferredCashflowProfile & Goals Sync                  │
        └─────────────────────────────────────────────────────────┘
```

> **Core Boundary Principle**: Phase 3.0 (`SPEC-003`) establishes only the module boundaries, event ingestion hooks, idempotency guards, and architectural rules. Downstream capabilities own their specific domain models and algorithms.

---

## 3. Mathematical & System Invariants

- **`I-INTEL-001` (Zero Ledger Mutation)**:
  The intelligence capability is strictly an **asynchronous analytical observer**. It MUST NEVER mutate `accounts` balances or write to `ledger`.
- **`I-INTEL-002` (In-Process Modulith Event Ingestion)**:
  Intelligence consumes ledger domain events through the Core's Spring Modulith event infrastructure and its durable Event Publication Registry. It MUST NOT consume internal domain events through NATS.
- **`I-INTEL-003` (Precision Policy: Monetary vs. Statistical)**:
  - All monetary values (`amount`, `liabilities`, `balances`) MUST use canonical `BigDecimal` with scale 2 (`RoundingMode.HALF_EVEN`).
  - Statistical calculations (`variance`, `standard deviation`, $CV_A$, `confidence` $\in [0, 1]$) MUST use `BigDecimal` with declared intermediate precision (minimum 6 decimal places) and MUST NOT use binary floating point (`I-TDD-002`).
- **`I-INTEL-004` (Strict API Boundary against Ledger Internals)**:
  Intelligence may consume stable ledger domain-event contracts (`br.com.wallet.ledger.api.event`), but MUST NOT depend on ledger internal persistence entities, repositories, transaction managers, or internal services.
- **`I-INTEL-005` (Hot-Path Airgap Invariant)**:
  Generative models, remote LLM APIs, and SLM inference engines SHALL NEVER execute on the financial transaction write path (`I-TYPED-001`):
  $$\text{Deps}(\text{SynchronousTxPath}) \cap \{\text{LLM}, \text{SLM}, \text{Ollama}, \text{SpringAI}\} = \emptyset$$
- **`I-INTEL-006` (Graceful Semantic Degradation & Anti-Coercion)**:
  When an optional semantic evaluator is unavailable or times out, its outcome is `DecisionUnavailable<T>` (`I-TYPED-005`). `DecisionComposer` is strictly prohibited from coercing unavailable outcomes into synthetic defaults (`I-TYPED-006`).
- **`I-INTEL-007` (Semantic Evaluator Failure Isolation)**:
  Failure, timeout, malformed output, model loading failure, or provider unavailability in semantic evaluators MUST NOT prevent candidate persistence, ledger processing, or downstream pipelines.
- **`I-INTEL-008` (Idempotent Event Processing)**:
  Intelligence event consumers MUST be idempotent with respect to the canonical `eventId` to guarantee idempotent side effects under at-least-once redelivery.
- **`I-INTEL-009` (Temporal Truth / No Future Leakage)**:
  Pattern detection and semantic evaluation for observation timestamp $t$ MUST use only evidence available at or before $t$.
- **`I-INTEL-010` (Strict Tenant Isolation Invariant)**:
  All intelligence domain models, events, persistence keys, and analytical state MUST be explicitly tenant-scoped (`I-SEC-005`). Cross-tenant aggregation or state commingling is strictly prohibited:
  $$\forall S \in \text{AnalyticalState}, \quad S.\text{tenantId} = \text{Event}.\text{tenantId}$$

---

## 4. Functional Requirements (Phase 3.0 Module Foundation — `I-SDD-004`)

### 4.1 Must Have (`[MUST]`)
- **`REQ-INTEL-001 [MUST]`**: Define `br.com.wallet.intelligence` as a Spring Modulith module (`package-info.java`) with dependencies restricted strictly to stable public API contracts: `ledger::api`, `core::api`, and `goals::api`. Dependency on root `core` or internal ledger packages is forbidden (`I-INTEL-004`).
- **`REQ-INTEL-002 [MUST]`**: Enforce Spring Modulith architectural boundaries (`ModulithArchitectureTest`) ensuring zero cyclic dependencies and no forbidden internal imports (`I-INTEL-004`).
- **`REQ-INTEL-003 [MUST]`**: Ingest `TransferCompletedEvent` and `WithdrawCompletedEvent` asynchronously via `@ApplicationModuleListener` backed by the PostgreSQL Event Publication Registry (`I-INTEL-002`).
- **`REQ-INTEL-004 [MUST]`**: Implement idempotent event processing based on canonical `eventId` to guarantee idempotent side effects under at-least-once redelivery (`I-INTEL-008`).
- **`REQ-INTEL-005 [MUST]`**: Enforce strict multi-tenant isolation (`tenantId` validation and propagation) across all module boundaries and event consumption (`I-INTEL-010`). Events without a valid `tenantId` are rejected (`TenantContextMissingException`).
- **`REQ-INTEL-006 [MUST]`**: Define foundation migration conventions for tenant-scoped intelligence persistence in `docker/init/schema.sql`, mandating `tenant_id VARCHAR(64) NOT NULL` and composite tenant indexes for all future capability tables (`I-INTEL-010`, `I-SDD-007`). Capability tables are owned by `SPEC-003.1` (`subscriptions`) and `SPEC-003.2` (`cashflow_forecasts`).
- **`REQ-INTEL-007 [MUST]`**: Assert zero ledger mutations (`I-INTEL-001`), zero internal NATS dependencies (`I-INTEL-002`), and complete tenant isolation (`I-INTEL-010`) via automated architecture tests (`ZeroLedgerMutationTest`, `NoInternalEventNatsDependencyTest`, `TenantIsolationArchitectureTest`).

### 4.2 Should Have (`[SHOULD]`)
- **`REQ-INTEL-008 [SHOULD]`**: Establish integration seams for `SPEC-000.8.1` Typed Decision Algebra contracts without implementing concrete evaluators in Phase 3.0 (`I-INTEL-006`, `I-INTEL-007`).

### 4.3 Won't Have (`[WON'T]`)
- **`REQ-INTEL-009 [WON'T]`**: Direct ledger or account balance mutations (`I-INTEL-001`).
- **`REQ-INTEL-010 [WON'T]`**: AI/SLM inference execution on transaction write path (`I-INTEL-005`).
- **`REQ-INTEL-011 [WON'T]`**: Concrete subscription recurrence clustering (owned by `SPEC-003.1`) or cashflow forecast calculations (owned by `SPEC-003.2`).

---

## 5. Architectural Topology

```text
src/main/java/br/com/wallet/intelligence
├── package-info.java                   (@ApplicationModule(allowedDependencies = {"ledger::api", "core::api", "goals::api"}))
├── api                                 (Public module contracts)
│   └── event                           (Module-level analytical events)
└── internal
    └── listener
        └── SpendingEventListener.java  (@ApplicationModuleListener consuming Transfer/Withdraw events with eventId idempotency)
```
> *Note: Capability-specific domain models (`Subscription`, `InferredCashflowProfile`) and evaluators are defined and owned by SPEC-003.1, SPEC-003.2, and SPEC-000.8.1.*

---

## 6. Verifiable Acceptance Criteria

- [ ] `REQ-INTEL-001` through `REQ-INTEL-007` [MUST] requirements pass automated unit and integration tests.
- [ ] Intelligence consumes internal Core events only through Spring Modulith Event Publication Registry (`I-INTEL-002`).
- [ ] Zero NATS dependency exists for internal intelligence events (`NoInternalEventNatsDependencyTest`).
- [ ] Intelligence event consumers guarantee idempotent side effects under redelivery (`I-INTEL-008`).
- [ ] Multi-tenant isolation verified: events from tenant A never leak into tenant B state (`I-INTEL-010`).
- [ ] `ModulithArchitectureTest.verifyArchitecture()` passes with 0 violations.
- [ ] Zero dependency from `intelligence` to `ledger` implementation packages, persistence entities, repositories, or transaction managers (`I-INTEL-004`).
- [ ] Zero ledger mutations occur from intelligence (`I-INTEL-001`).
- [ ] SPEC-003.1 and SPEC-003.2 capability slices cleanly extend this module foundation without breaking changes.
