---
name: spec-driven-development
description: GitHub Spec Kit (Spec-Driven Design) workflow orchestrator. Use when specifying, planning, analyzing, implementing, or verifying new features, refactors, or bugfixes.
---

# 📐 Spec-Driven Development (SDD) Skill

## 1. Identity & Objective

This skill implements the **GitHub Spec Kit** methodology adapted for financial and transactional systems. It orchestrates the lifecycle from user intent down to verified, production-ready code with complete mathematical traceability.

---

## 2. When to Activate

- Creating a new feature, use case, or API endpoint.
- Performing an architectural migration or refactoring.
- Fixing complex domain bugs with regression prevention.
- Creating formal specifications (`.spec/SPEC-XXX.md`), architecture plans (`.spec/PLAN-XXX.md`), or task lists (`.spec/TASKS-XXX.md`).

---

## 3. The 7-Stage SDD Pipeline

```text
1. SPECIFY   → Author .spec/SPEC-XXX.md (Intent, Invariants, Requirements)
2. CLARIFY   → Identify ambiguities, open questions, and domain edge cases
3. PLAN      → Author .spec/PLAN-XXX.md (Architecture, ADRs, Data Models)
4. TASKS     → Author .spec/TASKS-XXX.md (TDD step breakdown, Traceability)
5. ANALYZE   → Cross-check Spec ↔ Plan ↔ Tasks for consistency & gaps
6. IMPLEMENT → Execute tasks in TDD order (Red → Green → Refactor)
7. CONVERGE  → Generate Traceability Report and verify all acceptance criteria
```

---

## 4. Stage Procedures

### Stage 1: Specify
- Create `.spec/SPEC-XXX-<name>.md` using [`templates/spec-template.md`](file:///.agents/skills/spec-driven-development/templates/spec-template.md).
- Formulate requirements with unique IDs (`REQ-XXX`).
- Formulate mathematical system invariants (`I-XXX`).
- List non-goals and out-of-scope items explicitly.

### Stage 2: Clarify
- Review specification with human engineer.
- Resolve any open questions or assumptions.
- Do not proceed to planning until requirements and invariants are ratified.

### Stage 3: Plan
- Create `.spec/PLAN-XXX-<name>.md` using [`templates/plan-template.md`](file:///.agents/skills/spec-driven-development/templates/plan-template.md).
- Define technical architecture, module boundaries, and interfaces.
- Reference applicable Architecture Decision Records (ADRs).
- Detail data migrations, concurrency strategy, and failure handling.

### Stage 4: Tasks
- Create `.spec/TASKS-XXX-<name>.md` using [`templates/tasks-template.md`](file:///.agents/skills/spec-driven-development/templates/tasks-template.md).
- Break implementation into small, atomic TDD tasks.
- Every task must link to at least one requirement ID (`REQ-XXX`) or invariant (`I-XXX`).

### Stage 5: Analyze (Pre-Implementation Gate)
Verify the following consistency rules:
- Every `REQ-XXX` has corresponding tasks in `TASKS-XXX.md`.
- Every `I-XXX` has explicit validation or test coverage planned.
- No orphan tasks exist that lack specification backing.
- Concurrency, locking, and error paths are planned.

### Stage 6: Implement (TDD Execution)
- Follow the task list strictly in sequence.
- Write failing unit/integration tests first (`Red`).
- Implement minimal code to pass (`Green`).
- Refactor for clarity and performance while tests remain green.

### Stage 7: Converge
- Run full test suite (`./gradlew test`).
- Generate final Traceability Report comparing implementation evidence against acceptance criteria.
