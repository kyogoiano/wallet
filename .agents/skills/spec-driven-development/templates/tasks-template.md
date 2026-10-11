# 📝 Task Breakdown: TASKS-XXX — [Feature Title]

- **Associated Spec**: [`SPEC-XXX.md`](file:///.spec/SPEC-XXX.md)
- **Associated Plan**: [`PLAN-XXX.md`](file:///.spec/plans/PLAN-XXX.md)
- **Status**: Not Started | In Progress | Completed
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-XXX-001` | `[MUST]` | `shouldExecuteSuccessfully()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-XXX-002` | `[MUST]` | `shouldRejectInsufficientBalance()` | `TASK-2.1`, `TASK-2.2` |
| `I-LEDGER-002` | `[MUST]` | `shouldMaintainHashChainIntegrity()` | `TASK-1.3` |
| `REQ-XXX-003` | `[SHOULD]` | `shouldDegradeGracefullyOnCacheMiss()`| `TASK-3.1` |
| `REQ-XXX-004` | `[COULD]` | `shouldFilterWithOptionalParameters()`| `TASK-4.1` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> To prevent LLM context degradation ("lost in the middle"), when implementing a task, load ONLY the target task card into the working memory. Never hold unrelated files in context.

```markdown
### 🎯 Active Task Card: TASK-X.Y
- **Target Invariant**: I-XXX-001
- **Target Requirement**: REQ-XXX-001 [MUST]
- **Target Files**: <DomainService>.java, <DomainServiceTest>.java
- **In-Scope Contracts**: Inputs -> CommandDTO, Output -> ResultRecord
- **Forbidden Boundary**: Do not modify database schemas or unrelated services.
```

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Core Domain Models & Invariants ([MUST])
- [ ] `TASK-1.1` [RED]: Write unit tests for pure domain records, calculators, and mathematical invariants (`I-XXX-001`).
- [ ] `TASK-1.2` [GREEN]: Implement domain records with zero external I/O and immutable fields.
- [ ] `TASK-1.3` [REFACTOR]: Enforce canonical `BigDecimal` scale 2 (`isEqualByComparingTo`) and zero floating-point math.

### Phase 2: Persistence & Modulith Boundary ([MUST])
- [ ] `TASK-2.1` [RED]: Write Testcontainers integration tests for DAO queries, locking, and failure modes.
- [ ] `TASK-2.2` [GREEN]: Implement Spring JDBC DAO with explicit SQL parameter mapping and check constraints.
- [ ] `TASK-2.3` [REFACTOR]: Verify transaction isolation and index performance.

### Phase 3: Application Services & Core Gates ([MUST])
- [ ] `TASK-3.1` [RED]: Write unit/integration tests for application service and pre-execution gate evaluation.
- [ ] `TASK-3.2` [GREEN]: Implement Use Case service within `@Transactional` boundary.
- [ ] `TASK-3.3` [GREEN]: Wire pre-execution authorization gate and transactional outbox persistence.

### Phase 4: REST API & Exposure ([MUST])
- [ ] `TASK-4.1` [RED]: Write MockMvc tests verifying HTTP response status codes, headers, and validation errors.
- [ ] `TASK-4.2` [GREEN]: Implement REST controller implementing published API interface under `infrastructure.rest`.

### Phase 5: Resilience & Ergonomics ([SHOULD] / [COULD])
- [ ] `TASK-5.1` [RED/GREEN]: Implement retry backoff, circuit breaking, or cache fallback policies (`[SHOULD]`).
- [ ] `TASK-5.2` [RED/GREEN]: Implement optional query filters, pagination, or sorting (`[COULD]`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [ ] All unit tests pass: `./gradlew test`
- [ ] All integration tests pass: Testcontainers suite green
- [ ] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [ ] Zero compiler / linter warnings
- [ ] OpenTelemetry traces verified
- [ ] **Zero Spec-Drift Reconciliation (`I-SDD-003`)**: Class names, package paths, and DDL schemas in `SPEC-XXX` and `PLAN-XXX` match `src/` 100%
- [ ] All task checkboxes in `TASKS-XXX.md` marked `[x]`
- [ ] Author Practical Verification Guide & Seed Data in `SUMMARY-XXX.md` (`I-SDD-002`)
