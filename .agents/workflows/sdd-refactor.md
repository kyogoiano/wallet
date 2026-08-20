# 🔄 SDD Refactoring Workflow

This workflow guides architectural and structural refactoring with strict behavioral equivalence guarantees.

---

## Steps

### Step 1: Pre-Refactoring Safety Check
- Verify that comprehensive unit and integration tests exist for all affected components.
- If coverage is incomplete, write characterization tests first.

### Step 2: Architecture Plan (`.spec/PLAN-XXX-refactor.md`)
- Document current vs. target architecture.
- Identify affected interfaces, dependencies, and potential breaking changes.

### Step 3: Incremental Refactoring (Step-by-Step)
- Make small, localized structural transformations.
- Run tests after every step.
- Never mix structural refactoring with functional behavior changes in the same commit/step.

### Step 4: Verification & Performance Validation
- Run all unit and integration tests.
- Validate telemetry and performance metrics in OpenObserve.
