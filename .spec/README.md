# 📐 Specifications Directory (`.spec/`) — Master Index & Taxonomy

This directory contains specifications, architectural plans, task breakdowns, and execution summaries governing the **Wallet Service** under the **Spec-Driven Design (SDD) V2 Deterministic Framework**.

---

## 🗂️ Directory Taxonomy & File Conventions

All initiatives follow a strict, deterministic four-artifact lifecycle:

```text
.spec/
├── ROADMAP.md                                   # Multi-Phase Strategic Architecture & Milestones
├── README.md                                    # This Master Taxonomy & Navigation Index
├── SPEC-XXX-<slug>.md                           # Business Intent, Invariants, MoSCoW Requirements (<= 250 lines)
├── PLAN-XXX-<slug>.md                           # Modulith Boundaries, ADRs, Concurrency & DB Schemas
├── TASKS-XXX-<slug>.md                          # Atomic TDD Breakdown with Active Task Cards ([MUST] prioritized)
└── summaries/
    └── SUMMARY-XXX-<slug>.md                    # Traceability Audit, Test Coverage, Practical Verification Guide
```

---

## 🗺️ Master Initiatives Matrix (Phases 0 through 4)

### Tier 1: Core Architecture, Infrastructure & Resilience

| Phase | Module | Spec | Plan | Tasks | Summary | Status | Invariants Owned |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| **0.0** | `ledger` | [`SPEC-000`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md) | [`PLAN-000`](file:///.spec/PLAN-000-architecture-alignment-modulith-baseline.md) | [`TASKS-000`](file:///.spec/TASKS-000-architecture-alignment-modulith-baseline.md) | [`SUMMARY-000`](file:///.spec/summaries/SUMMARY-000-architecture-alignment-modulith-baseline.md) | 🟢 Verified | `I-MODULITH-001`, `I-MODULITH-002` |
| **0.1** | `fraud` / `infra` | [`SPEC-000.1`](file:///.spec/SPEC-000.1-migrate-redis-to-dragonflydb.md) | [`PLAN-000.1`](file:///.spec/PLAN-000.1-migrate-redis-to-dragonflydb.md) | [`TASKS-000.1`](file:///.spec/TASKS-000.1-migrate-redis-to-dragonflydb.md) | [`SUMMARY-000.1`](file:///.spec/summaries/SUMMARY-000.1-migrate-redis-to-dragonflydb.md) | 🟢 Verified | `I-DF-001` to `I-DF-005` |
| **0.2** | `dlq` | [`SPEC-000.2`](file:///.spec/SPEC-000.2-dlq-resilience-and-exhausted-operations.md) | [`PLAN-000.2`](file:///.spec/PLAN-000.2-dlq-resilience-and-exhausted-operations.md) | [`TASKS-000.2`](file:///.spec/TASKS-000.2-dlq-resilience-and-exhausted-operations.md) | [`SUMMARY-000.2`](file:///.spec/summaries/SUMMARY-000.2-dlq-resilience-and-exhausted-operations.md) | 🟢 Verified | `I-DLQ-001` to `I-DLQ-004` |
| **0.3** | `ledger` / `infra` | [`SPEC-000.3`](file:///.spec/SPEC-000.3-async-command-exception-handling.md) | [`PLAN-000.3`](file:///.spec/PLAN-000.3-async-command-exception-handling.md) | [`TASKS-000.3`](file:///.spec/TASKS-000.3-async-command-exception-handling.md) | [`SUMMARY-000.3`](file:///.spec/summaries/SUMMARY-000.3-async-command-exception-handling.md) | 🟢 Verified | `I-OPS-001` to `I-OPS-004` |
| **0.4** | `infra` / `core` | [`SPEC-000.4`](file:///.spec/SPEC-000.4-observability-outbox-and-openobserve-optimization.md) | [`PLAN-000.4`](file:///.spec/PLAN-000.4-observability-outbox-and-openobserve-optimization.md) | [`TASKS-000.4`](file:///.spec/TASKS-000.4-observability-outbox-and-openobserve-optimization.md) | [`SUMMARY-000.4`](file:///.spec/summaries/SUMMARY-000.4-observability-outbox-and-openobserve-optimization.md) | 🟢 Verified | `I-OBS-001`, `I-OBS-002` |

---

### Tier 2: Advanced Anti-Fraud, Graph & Machine Intelligence

| Phase | Module | Spec | Plan | Tasks | Summary | Status | Invariants Owned |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| **0.5** | `fraud.intelligence` | [`SPEC-000.5`](file:///.spec/SPEC-000.5-hybrid-fraud-intelligence-and-relational-graph.md) | [`PLAN-000.5`](file:///.spec/PLAN-000.5-hybrid-fraud-intelligence-and-relational-graph.md) | [`TASKS-000.5`](file:///.spec/TASKS-000.5-hybrid-fraud-intelligence-and-relational-graph.md) | [`SUMMARY-000.5`](file:///.spec/summaries/SUMMARY-000.5-hybrid-fraud-intelligence-and-relational-graph.md) | 🟢 Verified | `I-GRAPH-001` to `I-GRAPH-004` |
| **0.6** | `fraud.propagation` | [`SPEC-000.6`](file:///.spec/SPEC-000.6-fraud-risk-propagation-and-temporal-decay.md) | [`PLAN-000.6`](file:///.spec/PLAN-000.6-fraud-risk-propagation-and-temporal-decay.md) | [`TASKS-000.6`](file:///.spec/TASKS-000.6-fraud-risk-propagation-and-temporal-decay.md) | [`SUMMARY-000.6`](file:///.spec/summaries/SUMMARY-000.6-fraud-risk-propagation-and-temporal-decay.md) | 🟢 Verified | `I-PROP-001` to `I-PROP-005` |
| **0.7** | `fraud.embeddings` / `investigation` | [`SPEC-000.7`](file:///.spec/SPEC-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md) | [`PLAN-000.7`](file:///.spec/PLAN-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md) | [`TASKS-000.7`](file:///.spec/TASKS-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md) | [`SUMMARY-000.7`](file:///.spec/summaries/SUMMARY-000.7-fraud-behavioral-embeddings-and-investigation-pgvector.md) | 🟢 Verified | `I-VEC-001` to `I-VEC-010` |
| **0.8** | `fraud.fusion` | [`SPEC-000.8`](file:///.spec/SPEC-000.8-fraud-signal-fusion-and-micro-ml.md) | [`PLAN-000.8`](file:///.spec/PLAN-000.8-fraud-signal-fusion-and-micro-ml.md) | [`TASKS-000.8`](file:///.spec/TASKS-000.8-fraud-signal-fusion-and-micro-ml.md) | [`SUMMARY-000.8`](file:///.spec/summaries/SUMMARY-000.8-fraud-signal-fusion-and-micro-ml.md) | 🟢 Verified | `I-FUSION-001` to `I-FUSION-005` |

---

### Tier 3: Programmable Money, Account Lifecycle & Goals

| Phase | Module | Spec | Plan | Tasks | Summary | Status | Invariants Owned |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| **1.0** | `savings` | [`SPEC-001`](file:///.spec/SPEC-001-smart-savings-automation.md) | [`PLAN-001`](file:///.spec/PLAN-001-smart-savings-automation.md) | [`TASKS-001`](file:///.spec/TASKS-001-smart-savings-automation.md) | [`SUMMARY-001`](file:///.spec/summaries/SUMMARY-001-smart-savings-automation.md) | 🟢 Verified | `I-SAVINGS-001`, `I-SAVINGS-002` |
| **1.1** | `ledger` / `fraud` | [`SPEC-001.1`](file:///.spec/SPEC-001.1-account-lifecycle-state-and-fraud-blocking.md) | [`PLAN-001.1`](file:///.spec/PLAN-001.1-account-lifecycle-state-and-fraud-blocking.md) | [`TASKS-001.1`](file:///.spec/TASKS-001.1-account-lifecycle-state-and-fraud-blocking.md) | [`SUMMARY-001.1`](file:///.spec/summaries/SUMMARY-001.1-account-lifecycle-state-and-fraud-blocking.md) | 🟢 Verified | `I-ACCOUNT-001`, `I-ACC-002` |
| **1.2** | `savings` | [`SPEC-001.2`](file:///.spec/SPEC-001.2-savings-plans-and-rules-management.md) | [`PLAN-001.2`](file:///.spec/PLAN-001.2-savings-plans-and-rules-management.md) | [`TASKS-001.2`](file:///.spec/TASKS-001.2-savings-plans-and-rules-management.md) | [`SUMMARY-001.2`](file:///.spec/summaries/SUMMARY-001.2-savings-plans-and-rules-management.md) | 🟢 Verified | `I-RULE-001` to `I-RULE-004` |
| **2.0** | `goals` | [`SPEC-002`](file:///.spec/SPEC-002-financial-goal-engine.md) | [`PLAN-002`](file:///.spec/PLAN-002-financial-goal-engine.md) | [`TASKS-002`](file:///.spec/TASKS-002-financial-goal-engine.md) | [`SUMMARY-002`](file:///.spec/summaries/SUMMARY-002-financial-goal-engine.md) | 🟢 Verified | `I-GOAL-001` to `I-GOAL-005` |

---

### Tier 4: Future Capabilities Roadmap

| Phase | Module | Spec | Description | Status | Target Dependencies |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **3.0** | `intelligence` | `SPEC-003` | Subscription & Spending Intelligence (Recurring bills, merchant categorization, cashflow forecasting) | ⚪ Planned | `ledger.api`, `goals.api`, `fraud.embeddings` |
| **4.0** | `copilot` | `SPEC-004` | AI Financial Copilot & Model Context Protocol (MCP) Gateway with Human-in-the-Loop | ⚪ Planned | `goals.api`, `savings.api`, `intelligence.api` |

---

## 📋 Standard Authoring Templates

When creating new specifications, always instantiate from the canonical templates in `.agents/skills/spec-driven-development/templates/`:
- [Specification Template (`spec-template.md`)](file:///.agents/skills/spec-driven-development/templates/spec-template.md)
- [Architecture Plan Template (`plan-template.md`)](file:///.agents/skills/spec-driven-development/templates/plan-template.md)
- [Tasks & Active Card Template (`tasks-template.md`)](file:///.agents/skills/spec-driven-development/templates/tasks-template.md)
- [Execution Summary Template (`summary-template.md`)](file:///.agents/skills/spec-driven-development/templates/summary-template.md)

---

## 🔄 SDD V2 Deterministic Pipeline

Every new capability slice executes through the strict 8-stage gate:

```text
[0. Pre-Flight] ──> [1. Specify] ──> [2. Clarify] ──> [3. Plan] ──> [4. Tasks] ──> [5. Analyze] ──> [6. Implement] ──> [7. Converge]
```

- **MoSCoW Gate (`I-SDD-004`)**: All `[MUST]` tasks executed and verified first.
- **Bi-directional Equivalence Gate (`I-SDD-003`)**: Zero spec-code drift; schemas, docs, and code remain 100% congruent.
- **Zero Vibe Coding Gate (`I-TDD-002`)**: Mandatory test triads, canonical `BigDecimal` scale 2 arithmetic.
- **Practical Verification Gate (`I-SDD-002`)**: Reproducible CLI/cURL seed fixtures included in every summary.

