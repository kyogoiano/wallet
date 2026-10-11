# 🐛 SDD Bugfix Workflow (V2 Deterministic Edition)

This workflow ensures bugs are diagnosed, reproduced with deterministic tests, and resolved with zero regression or spec drift.

---

## Steps

### Step 0: Invariant & History Audit
- Identify the violated invariant (`I-XXX`) from [`.agents/rules/constitution.md`](file:///.agents/rules/constitution.md) or functional requirement (`REQ-XXX`).
- Audit preceding `.spec/summaries/SUMMARY-*.md` to review the original design contracts and known edge cases.
- If the bug introduces an architectural change or affects core invariants, create an incident issue note in the relevant `.spec/` artifact.

### Step 1: Write Failing Reproduction Test (RED — Zero Vibe Coding)
- Write an automated unit or integration test reproducing the exact failure mode.
- Adhere to **Zero Vibe Coding (`I-TDD-002`)**:
  - Assert exact monetary figures using `BigDecimal` scale 2 arithmetic (`isEqualByComparingTo()`).
  - Formulate the **Test Triad**: (1) Failing edge case, (2) Boundary verification, (3) Post-failure state check (e.g. zero partial ledger mutation).
- Run the test to confirm it fails specifically due to the reported issue and not due to compilation or setup errors.

### Step 2: Implement Minimal Fix (GREEN)
- Apply the minimal corrective code change in the targeted layer (`:core`, `:fraud`, or root application).
- Maintain all existing architectural boundaries (`RULE-CAP-001` - `RULE-CAP-007`).
- Confirm the reproduction test passes.

### Step 3: Refactor & Regression Gate
- Clean up the fix, removing any temporary debugging code.
- Run the full test suite (`./gradlew test`) to guarantee zero collateral regressions.
- Verify Spring Modulith boundaries (`ModulithArchitectureTest.verifyArchitecture()`).

### Step 4: Bi-directional Reconciliation & Invariant Update (CONVERGE)
- Enforce **Bi-directional Equivalence (`I-SDD-003`)**: If the bug revealed an underspecified requirement or invalid assumption, update the corresponding `SPEC-XXX.md`, `PLAN-XXX.md`, or `constitution.md`.
- Document the post-mortem, root cause, and regression test reference in the active spec or summary.

