# 📝 Multi-Pass Spec Extraction Prompt Template

Use this template when invoking a `spec-analyst` or extracting a new specification slice from ambiguous user instructions or business discussions.

---

## Input Prompt Context
- **Raw Feature Request**: `[Insert user input or problem description]`
- **Preceding Histories & ADRs**: `[List relevant .histories/ and summaries/SUMMARY-*.md]`
- **Governing Invariants**: `[List non-negotiable constitution.md invariants: I-LEDGER-001, I-BALANCE-002, etc.]`
- **Target Capability Module**: `[E.g., br.com.wallet.intelligence]`

---

## Step 1: Multi-Pass Execution Instructions

### Pass 1: Product Intent & Behavioral Extraction
1. Formulate the core User Problem and Business Value.
2. Outline the primary User Journey and observable interaction.
3. List Functional Requirements (`REQ-XXX`), tagging every item with MoSCoW (`[MUST]`, `[SHOULD]`, `[COULD]`, `[WON'T]`).
4. Ensure zero leakage of internal implementation classes or private helper methods.

### Pass 2: Technical Intent & Boundary Extraction
1. Identify module boundaries under Spring Modulith.
2. Formulate formal Mathematical Invariants (`I-XXX`) using LaTeX notation.
3. Construct the **Cross-Feature Impact Matrix**:
   - Concurrency & Row-level Locking (`SELECT FOR UPDATE`)
   - Account Lifecycle State (`I-ACCOUNT-001`)
   - Fraud Gate SLA ($P99 < 2\text{ms}$)
   - Outbox Delivery & Event Streaming
   - Downstream Module Listeners
4. Specify storage schemas (PostgreSQL DDL, DragonflyDB keys, Lua scripts).

### Pass 3: Deterministic Test Triads (`I-TDD-002`)
For every `REQ-XXX [MUST]` requirement, define:
1. Positive Happy Path scenario with expected final balance and ledger hash chaining.
2. Invalid Input scenario (validation rejection).
3. Invariant Breach scenario (financial constraint rejection).
Enforce exact `BigDecimal` scale 2 arithmetic.

### Pass 4: Token-Compliant Slicing (`I-SDD-006`)
1. Measure line count: strictly $\le 250$ lines.
2. If exceeded, split into dot-releases (`SPEC-XXX.1`, `SPEC-XXX.2`).
3. Generate Active Task Cards for `tasks/TASKS-XXX.md`.

---

## Output Target Locations
- Specification: `.spec/SPEC-XXX-<name>.md`
- Architecture Plan: `.spec/plans/PLAN-XXX-<name>.md`
- Task Breakdown: `.spec/tasks/TASKS-XXX-<name>.md`
