# 📐 Active Specifications Directory (`.spec/`)

This directory contains feature specifications, architectural plans, and task breakdowns following the **Spec-Driven Design (SDD)** and **GitHub Spec Kit** methodology.

---

## 🗂️ File Naming Convention

Each initiative uses a numeric prefix:

- **Roadmap**: [`ROADMAP.md`](file:///.spec/ROADMAP.md) — 5-Phase Feature Evolution & Capability Architecture
- **Specification**: `SPEC-XXX-<name>.md` (e.g. `SPEC-001-wallet-capability-platform.md`)
- **Architecture Plan**: `PLAN-XXX-<name>.md` (e.g. `PLAN-001-wallet-capability-platform.md`)
- **Task Tracker**: `TASKS-XXX-<name>.md` (e.g. `TASKS-001-wallet-capability-platform.md`)

---

## 🗺️ Active Initiatives

| Phase | Spec | Title | Status |
| :--- | :--- | :--- | :--- |
| **0.0** | [`SPEC-000`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md) | Architecture Alignment & Modulith Baseline (Refactor) | 🟡 Draft / Clarification |
| **0** | [`SPEC-001`](file:///.spec/SPEC-001-wallet-capability-platform.md) | Wallet Modular Capability Platform (Spring Modulith) | 🟡 Draft / Clarification |
| **1** | `SPEC-002` | Smart Savings & Programmable Money | ⚪ Planned |
| **2** | `SPEC-003` | Financial Goal & Cashflow Engine | ⚪ Planned |
| **3** | `SPEC-004` | Subscription & Spending Intelligence | ⚪ Planned |
| **4** | `SPEC-005` | AI Financial Copilot & MCP Gateway | ⚪ Planned |

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
