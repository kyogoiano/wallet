---
name: spec-driven-development
description: GitHub Spec Kit (Spec-Driven Design) workflow orchestrator. Use when specifying, planning, analyzing, implementing, or verifying new features, refactors, or bugfixes.
---

# 📐 Spec-Driven Development (SDD) Skill — V2 Deterministic Edition

## 1. Identity & Objective

This skill implements the **GitHub Spec Kit** methodology rigorously adapted for financial engineering, high-throughput transactional ledgers, and distributed risk systems. It eliminates **"vibe coding"**, prevents **spec-implementation drift**, safeguards against the **"lost in the middle"** LLM context degradation problem, and guarantees mathematical determinism through strict TDD verification.

---

## 2. When to Activate

- Defining a new capability module, domain use case, or API endpoint.
- Executing an architectural refactoring, migration, or boundary shift.
- Diagnosing or fixing financial domain bugs with regression prevention.
- Authoring or auditing `.spec/SPEC-XXX.md`, `.spec/plans/PLAN-XXX.md`, `.spec/tasks/TASKS-XXX.md`, or `.spec/summaries/SUMMARY-XXX.md`.

---

## 3. The 8-Stage Enhanced SDD Pipeline

```text
0. PRE-FLIGHT  → Audit .histories/ and prior SUMMARY-*.md for past ADRs and invariants
1. SPECIFY     → Author .spec/SPEC-XXX.md (Product & Tech Intent, MoSCoW, Impact Matrix)
2. CLARIFY     → Resolve domain ambiguities, edge cases, and human alignment
3. PLAN        → Author .spec/plans/PLAN-XXX.md (Architecture, Modulith Boundaries, ADRs)
4. TASKS       → Author .spec/tasks/TASKS-XXX.md (Atomic TDD task cards, [MUST] prioritized)
5. ANALYZE     → Pre-implementation consistency gate (Spec ↔ Plan ↔ Tasks traceability)
6. IMPLEMENT   → Red → Green → Refactor (Zero Vibe Coding, Exact BigDecimal math)
7. CONVERGE    → Zero Spec-Drift Reconciliation, Modulith verification, Practical Guide
```


---

## 4. Stage Procedures & Governance Gates

### Stage 0: Pre-Flight History & Context Audit
- **Goal**: Intentional curation avoiding regression of past architectural decisions.
- **Actions**:
  1. Inspect `.histories/` for preceding discussions and design tradeoffs in the domain.
  2. Review relevant `.spec/summaries/SUMMARY-*.md` documents.
  3. Identify foundational invariants (`constitution.md`) that constrain the new feature.

### Stage 1: Specify (`I-SDD-004`, `I-SDD-005`, `I-SDD-006`)
- **Goal**: Formulate user intent with mathematical precision within a bounded context.
- **Protocol**: Apply the [`design-spec-extraction`](../design-spec-extraction/SKILL.md) skill to execute multi-pass extraction, separating product and technical intent while focusing strictly on observable behavior over implementation.
- **Actions**:
  1. Create `.spec/SPEC-XXX-<name>.md` using [`templates/spec-template.md`](templates/spec-template.md).
  2. **Atomic Spec Slicing (`I-SDD-006`)**: Limit spec to a single Bounded Context and $\le 250$ lines. Decompose large features into sequential dot-releases (`SPEC-XXX.1`, `SPEC-XXX.2`).
  3. **MoSCoW Prioritization (`I-SDD-004`)**: Tag every requirement explicitly:
     - `[MUST]`: Essential architectural/financial invariants and core contracts.
     - `[SHOULD]`: Operational resilience, retries, exponential backoff, telemetry counters.
     - `[COULD]`: Ergonomic shortcuts, optional filters, human-friendly formatting.
     - `[WON'T]`: Explicitly out-of-scope boundaries for this iteration.
  4. **Cross-Feature Impact Matrix (`I-SDD-005`)**: Systematically map impacts across `ledger`, `fraud`, `savings`, `goals`, `dlq`, and `messaging`.
  5. Define system invariants (`I-XXX`) using formal mathematical notation.


### Stage 2: Clarify
- **Goal**: Human alignment before architectural commitment.
- **Actions**:
  1. Present open trade-offs and edge cases to the human engineer.
  2. Resolve ambiguous domain policies. Do not proceed until requirements and invariants are ratified.

### Stage 3: Plan
- **Goal**: Architecture decisions, Modulith module boundaries, data structures, and failure semantics.
- **Actions**:
  1. Create `.spec/plans/PLAN-XXX-<name>.md` using [`templates/plan-template.md`](templates/plan-template.md).
  2. Define package topology conforming to Spring Modulith (`api` vs `internal`).
  3. Document ADRs, sequence diagrams, and concurrency strategy (`SELECT FOR UPDATE` deterministic ordering).
  4. Specify database DDL migrations, check constraints, indexes, and cache TTLs.

### Stage 4: Tasks (Active Task Cards)
- **Goal**: Atomic, sequence-ordered TDD task decomposition mapped 1-to-1 to requirements.
- **Actions**:
  1. Create `.spec/tasks/TASKS-XXX-<name>.md` using [`templates/tasks-template.md`](templates/tasks-template.md).
  2. Group tasks strictly by MoSCoW tier: **Phase 1 executes `[MUST]` tasks only**.
  3. Ensure every task points to its target requirement ID (`REQ-XXX`) or invariant (`I-XXX`).


### Stage 5: Analyze (Pre-Implementation Gate)
- **Goal**: Automated consistency verification before a single line of production code is written.
- **Actions**:
  1. Verify: Every `REQ-XXX [MUST]` has corresponding test tasks in `TASKS-XXX.md`.
  2. Verify: Every `I-XXX` invariant maps to an explicit assertion test.
  3. Verify: Zero orphan tasks exist without specification backing.
  4. Verify: Concurrency, error paths, and seed data prerequisites are fully specified.

### Stage 6: Implement (Zero Vibe Coding — `I-TDD-002`)
- **Goal**: Deterministic Red $\to$ Green $\to$ Refactor execution.
- **Actions**:
  1. **Red**: Write a failing unit/integration test. Verify that it fails for the expected domain reason.
  2. **Mandatory Test Triad**: Positive path + Invalid input gate + Invariant breach rejection.
  3. **Exact Arithmetic**: Canonical `BigDecimal` scale 2 via `isEqualByComparingTo()`. Zero floats/doubles.
  4. **Green**: Write the minimal production code necessary to pass.
  5. **Refactor**: Clean up and optimize while all tests remain green.

### Stage 7: Converge (`I-SDD-002`, `I-SDD-003`)
- **Goal**: Bi-directional equivalence certification, zero-drift verification, and operational documentation.
- **Actions**:
  1. **Build & Test**: Run `./gradlew test jacocoTestReport`.
  2. **Modulith Verification**: Run `ModulithArchitectureTest.verifyArchitecture()` (0 violations, 0 cycles).
  3. **Coverage Check**: Verify line coverage meets thresholds ($\ge 70\%$ overall, $\ge 85\%$ core domain/fraud).
  4. **Zero Spec-Drift Reconciliation (`I-SDD-003`)**: Reconcile all class names, package paths, and DDL schemas in `SPEC-XXX` and `PLAN-XXX` to match the final codebase 100%. Update all checkboxes in `TASKS-XXX` to `[x]`.
  5. **Practical Verification Guide (`I-SDD-002`)**: Author complete manual testing guide with seed data, CLI `curl` commands, NATS events, and SQL/Dragonfly assertion queries in `SUMMARY-XXX.md`.

---

## 5. Context Hygiene & "Lost in the Middle" Mitigation

To prevent context window degradation and LLM recall loss over long sessions:

### 5.1 The Active Task Card Protocol
When implementing, bring **only** the active task into the working set:
```markdown
### 🎯 Active Task Card: TASK-X.Y
- **Target Invariant**: I-XXX-001
- **Target Requirement**: REQ-XXX-001 [MUST]
- **Target Files**: <DomainService>.java, <DomainServiceTest>.java
- **In-Scope Contracts**: Inputs -> CommandDTO, Output -> ResultRecord
- **Forbidden Boundary**: Do not modify database schemas or unrelated services.
```

### 5.2 Progressive Context Loading (L0–L4 Hierarchy)
- Never view entire multi-thousand line files. Use line slices (`StartLine`/`EndLine`).
- Never run commands producing multi-megabyte terminal outputs.
- Close subagent contexts after isolated tasks finish to reclaim attention capacity.

---

## 6. Strategic Subagent Team Topology

When executing complex phases, delegate to specialized subagents:

| Subagent Role | Type Name | Tools / Mode | Primary Responsibility |
| :--- | :--- | :--- | :--- |
| **System Researcher** | `research` | Read-only | Explore codebase, review `.histories/`, build cross-feature impact matrices. |
| **Spec Analyst** | `research` | Read-only | Stage 5 Pre-Implementation Gate: audit Spec ↔ Plan ↔ Tasks consistency. |
| **TDD Implementer** | `self` | Write / Branch | Red $\to$ Green $\to$ Refactor execution for a single Active Task Card. |
| **Convergence Auditor** | `self` | Read / Command | Run `./gradlew test`, check Modulith compliance, assert Zero Spec-Drift. |
