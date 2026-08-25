# 🤖 Antigravity Agent Guide — Wallet Service

Welcome to the **Wallet Service** codebase. This repository uses **Spec-Driven Design (SDD)** and **Antigravity Customizations** to ensure reliable, high-performance financial engineering.

---

## 🧠 Context Hierarchy & Progressive Disclosure

To maximize reasoning efficiency and prevent context window degradation, follow the **5-layer cache hierarchy**:

| Layer | Type | Location | Purpose |
| :--- | :--- | :--- | :--- |
| **L0: Constitution** | Immutable Constraints | [`.agents/rules/constitution.md`](file:///.agents/rules/constitution.md) | Absolute financial & architectural invariants (never violate). |
| **L1: Rules** | Persistent Context | [`.agents/rules/`](file:///.agents/rules/) | Architectural standards, coding conventions, testing guidelines. |
| **L2: Skills** | On-Demand Knowledge | [`.agents/skills/`](file:///.agents/skills/) | Procedural domain workflows (loaded progressively when needed). |
| **L3: Specifications**| Working Set | [`.spec/`](file:///.spec/) | Feature requirements, invariant definitions, tasks & plans. |
| **L4: Source Code** | Implementation Evidence | [`src/`](file:///src/), [`core/`](file:///core/), [`fraud/`](file:///fraud/) | Targeted files modified strictly within specification bounds. |

> **Rule of Thumb**: Never load more context into memory than needed for the active step. Read references only when specifically relevant.

---

## 🔄 Spec-Driven Development (SDD) Pipeline

Every significant feature, refactor, or architectural change follows the **Spec Kit Pipeline**:

```mermaid
flowchart LR
    Specify --> Clarify --> Plan --> Tasks --> Analyze --> Implement --> Converge
```

1. **Specify** (`.spec/SPEC-XXX.md`): Define user intent, functional requirements, non-goals, and **mathematical invariants**.
2. **Clarify**: Resolve ambiguities and edge cases with the human engineer.
3. **Plan** (`.spec/PLAN-XXX.md`): Architecture decisions, interface contracts, module boundaries, data structures.
4. **Tasks** (`.spec/TASKS-XXX.md`): TDD tasks, implementation order, verification plan.
5. **Analyze**: Verify consistency across Spec, Plan, and Tasks before writing code.
6. **Implement**: TDD execution (Red $\rightarrow$ Green $\rightarrow$ Refactor) in bounded increments.
7. **Converge**: Traceability verification (every requirement mapped to passing tests and zero orphan code).

---

## 🗂️ Active Customization Index

### Rules ([`.agents/rules/`](file:///.agents/rules/))
- [`constitution.md`](file:///.agents/rules/constitution.md) — Absolute financial invariants & core non-negotiable rules.
- [`project-context.md`](file:///.agents/rules/project-context.md) — Architecture overview, tech stack, and module boundaries.
- [`capability-boundaries.md`](file:///.agents/rules/capability-boundaries.md) — Architectural mantra, Spring Modulith boundaries, and capability rules.
- [`coding-standards.md`](file:///.agents/rules/coding-standards.md) — Modern Java 26 patterns, immutability, zero boilerplate.
- [`testing-standards.md`](file:///.agents/rules/testing-standards.md) — TDD methodology, Testcontainers, resilience, and ledger validation.

### Skills ([`.agents/skills/`](file:///.agents/skills/))
- [`spec-driven-development`](file:///.agents/skills/spec-driven-development/SKILL.md) — Spec Kit orchestration, templates, and verification.
- [`capability-driven-development`](file:///.agents/skills/capability-driven-development/SKILL.md) — Spring Modulith capability building, action lifecycle, and boundaries.
- [`ledger-engineering`](file:///.agents/skills/ledger-engineering/SKILL.md) — Hash-chained tamper detection, atomic transfers, ledger reconstruction.
- [`antifraud-engineering`](file:///.agents/skills/antifraud-engineering/SKILL.md) — Multi-tier fraud detection, sliding windows, Lua scripts, Caffeine/Redis caching.
- [`outbox-messaging`](file:///.agents/skills/outbox-messaging/SKILL.md) — Transactional outbox pattern, NATS JetStream, deduplication, retry exponential backoff.
- [`observability-tracing`](file:///.agents/skills/observability-tracing/SKILL.md) — OpenTelemetry spans, baggage propagation (`operationId`), OTLP exports.

### Workflows ([`.agents/workflows/`](file:///.agents/workflows/))
- [`sdd-feature.md`](file:///.agents/workflows/sdd-feature.md) — End-to-end workflow for implementing new features.
- [`sdd-bugfix.md`](file:///.agents/workflows/sdd-bugfix.md) — Reproduction and fix workflow with regression tests.
- [`sdd-refactor.md`](file:///.agents/workflows/sdd-refactor.md) — Safe refactoring with behavioral equivalence guarantees.
