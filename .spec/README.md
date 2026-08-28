# 📐 Active Specifications Directory (`.spec/`)

This directory contains feature specifications, architectural plans, and task breakdowns following the **Spec-Driven Design (SDD)** and **GitHub Spec Kit** methodology.

---

## 🗂️ File Naming Convention

Each initiative uses a numeric prefix:

- **Roadmap**: [`ROADMAP.md`](file:///.spec/ROADMAP.md) — 6-Phase Feature Evolution & Capability Architecture
- **Specification**: `SPEC-XXX-<name>.md` (e.g. `SPEC-001-smart-savings-automation.md`)
- **Architecture Plan**: `PLAN-XXX-<name>.md` (e.g. `PLAN-001-smart-savings-automation.md`)
- **Task Tracker**: `TASKS-XXX-<name>.md` (e.g. `TASKS-001-smart-savings-automation.md`)
- **Execution Summary**: `summaries/SUMMARY-XXX-<name>.md` (e.g. `summaries/SUMMARY-000-architecture-alignment-modulith-baseline.md`)

---

## 🗺️ Active Initiatives

| Phase | Spec | Title | Status | Execution Summary |
| :--- | :--- | :--- | :--- | :--- |
| **0** | [`SPEC-000`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md) | Architecture Alignment & Modulith Baseline (Refactor) | 🟢 Completed & Reconciled | [`SUMMARY-000`](file:///.spec/summaries/SUMMARY-000-architecture-alignment-modulith-baseline.md) |
| **0.1** | [`SPEC-000.1`](file:///.spec/SPEC-000.1-migrate-redis-to-dragonflydb.md) | In-Memory Store Migration: Redis to DragonflyDB | 🟢 Completed & Reconciled | [`SUMMARY-000.1`](file:///.spec/summaries/SUMMARY-000.1-migrate-redis-to-dragonflydb.md) |
| **1** | [`SPEC-001`](file:///.spec/SPEC-001-smart-savings-automation.md) | Smart Savings Automation (`br.com.wallet.savings`) | 🟢 Completed & Reconciled | [`SUMMARY-001`](file:///.spec/summaries/SUMMARY-001-smart-savings-automation.md) |
| **1.1** | [`SPEC-001.1`](file:///.spec/SPEC-001.1-account-lifecycle-state-and-fraud-blocking.md) | Account Lifecycle State & Fraud Blocking (`br.com.wallet.ledger`) | 🟢 Completed & Reconciled | [`SUMMARY-001.1`](file:///.spec/summaries/SUMMARY-001.1-account-lifecycle-state-and-fraud-blocking.md) |
| **1.2** | [`SPEC-001.2`](file:///.spec/SPEC-001.2-savings-plans-and-rules-management.md) | Savings Plans & Rules Dynamic Management (`br.com.wallet.savings`) | 🟢 Completed & Reconciled | [`SUMMARY-001.2`](file:///.spec/summaries/SUMMARY-001.2-savings-plans-and-rules-management.md) |
| **2** | [`SPEC-002`](file:///.spec/SPEC-002-financial-goal-engine.md) | Financial Goal & Cashflow Engine (`br.com.wallet.goals`) | 🟢 Completed & Reconciled | [`SUMMARY-002`](file:///.spec/summaries/SUMMARY-002-financial-goal-engine.md) |
| **3** | `SPEC-003` | Subscription & Spending Intelligence (`br.com.wallet.intelligence`) | ⚪ Planned | — |
| **4** | `SPEC-004` | AI Financial Copilot & MCP Gateway (`br.com.wallet.copilot`) | ⚪ Planned | — |

---

## 📋 Templates

Templates for authoring new specifications are located in:
- [`spec-template.md`](file:///.agents/skills/spec-driven-development/templates/spec-template.md)
- [`plan-template.md`](file:///.agents/skills/spec-driven-development/templates/plan-template.md)
- [`tasks-template.md`](file:///.agents/skills/spec-driven-development/templates/tasks-template.md)

---

## 🔄 SDD Pipeline Summary

1. **Specify**: Author `SPEC-XXX.md` (Intent, Invariants, Requirements) $\rightarrow$ *Gate 1 (Ratification)*
2. **Plan**: Author `PLAN-XXX.md` (Architecture, ADRs, Data Models) $\rightarrow$ *Gate 2 (Sign-off)*
3. **Tasks**: Author `TASKS-XXX.md` (TDD tasks with traceability matrix)
4. **Implement**: TDD execution (Red $\rightarrow$ Green $\rightarrow$ Refactor)
5. **Converge**: Traceability report and full test suite verification
