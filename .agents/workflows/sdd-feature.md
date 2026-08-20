# 🚀 SDD Feature Workflow

This workflow guides the implementation of a new feature from specification to verified code.

---

## Steps

### Step 1: Specification (`.spec/SPEC-XXX-<feature>.md`)
- Create `.spec/SPEC-XXX-<feature>.md` using `.agents/skills/spec-driven-development/templates/spec-template.md`.
- Fill in: Intent, Scope, Non-goals, Requirements (`REQ-XXX`), Mathematical Invariants (`I-XXX`), Interface Contracts, and Acceptance Criteria.
- **GATE 1 (Human Review)**: Present the specification to the user and request ratification.

### Step 2: Architecture Planning (`.spec/PLAN-XXX-<feature>.md`)
- Create `.spec/PLAN-XXX-<feature>.md` using `.agents/skills/spec-driven-development/templates/plan-template.md`.
- Detail module impacts (`:core`, `:fraud`, root application), SQL schemas, locking strategy, and Redis data models.
- **GATE 2 (Human Review)**: Present the plan to the user for architectural sign-off.

### Step 3: Task Breakdown & Traceability (`.spec/TASKS-XXX-<feature>.md`)
- Create `.spec/TASKS-XXX-<feature>.md` using `.agents/skills/spec-driven-development/templates/tasks-template.md`.
- Build the traceability matrix mapping every `REQ-XXX` and `I-XXX` to tests.
- Sequence tasks in TDD order (Domain $\rightarrow$ Persistence $\rightarrow$ Use Case $\rightarrow$ API).

### Step 4: Consistency Analysis
- Cross-check Spec $\leftrightarrow$ Plan $\leftrightarrow$ Tasks. Ensure zero orphan tasks and zero unverified requirements.

### Step 5: TDD Implementation
- Execute tasks incrementally.
- Write failing unit/integration tests (`Red`).
- Write minimal code to pass (`Green`).
- Refactor code without altering observable behavior (`Refactor`).

### Step 6: Verification & Convergence
- Run the test suite: `./gradlew test`
- Generate Traceability Report verifying all acceptance criteria.
