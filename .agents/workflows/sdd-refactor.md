# 🔄 SDD Refactoring Workflow (V2 Deterministic Edition)

This workflow guides architectural and structural refactoring with strict behavioral equivalence guarantees and zero regression.

---

## Steps

### Step 0: Pre-Flight Audit & Characterization Safety Gate
- Audit existing test coverage across targeted packages (`build/reports/jacoco/test/html/index.html`).
- If test coverage is incomplete or lacks negative scenarios, write characterization tests first.
- Confirm all existing tests pass green (`./gradlew test`) before altering any structure.

### Step 1: Architecture Plan (`.spec/plans/PLAN-XXX-refactor.md`)

- Document current vs. target architecture, package restructuring, and dependency flows.
- Confirm compliance with Spring Modulith capability boundaries (`RULE-CAP-001` - `RULE-CAP-007`).
- Identify all affected published interfaces (`.api.*`) and verify backward compatibility.

### Step 2: Incremental Refactoring (Step-by-Step)
- Make small, localized structural transformations.
- Never mix structural refactoring with functional behavior changes in the same commit or step.
- Run tests continuously after every discrete refactor step.

### Step 3: Verification & Performance Validation
- Run full automated test suite: `./gradlew test`.
- Verify Spring Modulith architectural boundaries (`ModulithArchitectureTest.verifyArchitecture()`).
- Verify telemetry spans, metrics, and latency characteristics in OpenObserve.

### Step 4: Bi-directional Equivalence & Summary (CONVERGE)
- Enforce **Bi-directional Equivalence (`I-SDD-003`)**: Ensure all architecture documentation and diagrams reflect the new structure.
- Author or update `.spec/summaries/SUMMARY-XXX-<refactor>.md` detailing structural improvements and verification results.

