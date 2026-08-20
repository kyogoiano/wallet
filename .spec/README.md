# 📐 Active Specifications Directory (`.spec/`)

This directory contains feature specifications, architectural plans, and task breakdowns following the **Spec-Driven Design (SDD)** and **GitHub Spec Kit** methodology.

---

## 🗂️ File Naming Convention

Each initiative uses a numeric prefix:

- **Specification**: `SPEC-XXX-<name>.md` (e.g. `SPEC-001-multi-currency.md`)
- **Architecture Plan**: `PLAN-XXX-<name>.md` (e.g. `PLAN-001-multi-currency.md`)
- **Task Tracker**: `TASKS-XXX-<name>.md` (e.g. `TASKS-001-multi-currency.md`)

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
