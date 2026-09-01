# 🚀 SDD Feature Workflow

This workflow guides the implementation of a new feature from specification to verified code.

---

## Steps

### Step 1: Specification (`.spec/SPEC-XXX-<feature>.md`)
- Create `.spec/SPEC-XXX-<feature>.md` using `.agents/skills/spec-driven-development/templates/spec-template.md`.
- Fill in: Intent, Scope, Non-goals, Requirements (`REQ-XXX`), Mathematical Invariants (`I-XXX`), Interface Contracts, Practical Verification Scenarios, and Acceptance Criteria.
- **GATE 1 (Human Review)**: Present the specification to the user and request ratification.

### Step 2: Architecture Planning (`.spec/PLAN-XXX-<feature>.md`)
- Create `.spec/PLAN-XXX-<feature>.md` using `.agents/skills/spec-driven-development/templates/plan-template.md`.
- Detail module impacts (`:core`, `:fraud`, root application), SQL schemas, seed data additions, locking strategy, and Redis data models.
- **GATE 2 (Human Review)**: Present the plan to the user for architectural sign-off.

### Step 3: Task Breakdown & Traceability (`.spec/TASKS-XXX-<feature>.md`)
- Create `.spec/TASKS-XXX-<feature>.md` using `.agents/skills/spec-driven-development/templates/tasks-template.md`.
- Build the traceability matrix mapping every `REQ-XXX` and `I-XXX` to tests.
- Include tasks for seed data additions and authoring the Practical Verification Guide in the summary.
- Sequence tasks in TDD order (Domain $\rightarrow$ Persistence $\rightarrow$ Use Case $\rightarrow$ API).

### Step 4: Consistency Analysis
- Cross-check Spec $\leftrightarrow$ Plan $\leftrightarrow$ Tasks. Ensure zero orphan tasks, zero unverified requirements, and planned practical verification.

### Step 5: TDD Implementation
- Execute tasks incrementally.
- Write failing unit/integration tests (`Red`).
- Write minimal code to pass (`Green`).
- Refactor code without altering observable behavior (`Refactor`).

### Step 6: Verification & Convergence
- Run the test suite: `./gradlew test`
- Verify Spring Modulith boundaries (`ModulithArchitectureTest.verifyArchitecture()`).
- Author `.spec/summaries/SUMMARY-XXX-<feature>.md` with Traceability Verification, Code Coverage, and the **Practical Verification Guide & Seed Data** (`I-SDD-002`).

