# 🐛 SDD Bugfix Workflow

This workflow ensures bugs are diagnosed, reproduced with failing tests, and fixed without regression.

---

## Steps

### Step 1: Issue Analysis & Invariant Identification
- Identify the violated invariant (`I-XXX`) or functional expectation (`REQ-XXX`).
- Create or update the incident record in `.spec/` if the bug is high impact.

### Step 2: Write Failing Reproduction Test (RED)
- Write an automated unit or integration test that reproduces the exact bug scenario.
- Run test to confirm it fails specifically due to the reported issue.

### Step 3: Implement Fix (GREEN)
- Apply the minimal corrective code change in the targeted layer (`:core`, `:fraud`, or root application).
- Run the reproduction test and confirm it passes.

### Step 4: Regression Check
- Run the full test suite (`./gradlew test`) to ensure zero collateral regressions.

### Step 5: Document Post-Mortem
- Document root cause and preventive measures in the associated specification.
