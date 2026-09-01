# 📝 Task Breakdown: TASKS-XXX — [Feature Title]

- **Associated Spec**: [`SPEC-XXX.md`](file:///.spec/SPEC-XXX.md)
- **Associated Plan**: [`PLAN-XXX.md`](file:///.spec/PLAN-XXX.md)
- **Status**: Not Started | In Progress | Completed

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-XXX-001` | `shouldExecuteSuccessfully()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-XXX-002` | `shouldRejectInsufficientBalance()` | `TASK-2.1` |
| `I-LEDGER-002` | `shouldMaintainHashChainIntegrity()` | `TASK-3.1` |
| `I-IDEMPOTENCY-001` | `shouldHandleReplayIdempotently()` | `TASK-4.1` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Domain & Core Types
- [ ] `TASK-1.1` [RED]: Write unit test for domain model / value objects in `:core`.
- [ ] `TASK-1.2` [GREEN]: Implement domain records and invariants.
- [ ] `TASK-1.3` [REFACTOR]: Clean domain classes and ensure immutability.

### Phase 2: Persistence & Infrastructure
- [ ] `TASK-2.1` [RED]: Write integration test for DAO / repository operations against Testcontainers PostgreSQL.
- [ ] `TASK-2.2` [GREEN]: Implement SQL queries, locking, and mapper logic.
- [ ] `TASK-2.3` [REFACTOR]: Optimize queries and connection pooling.

### Phase 3: Application Use Case & Orchestration
- [ ] `TASK-3.1` [RED]: Write use case integration test verifying atomicity and ledger entries.
- [ ] `TASK-3.2` [GREEN]: Implement use case logic with `@Transactional` boundary.
- [ ] `TASK-3.3` [GREEN]: Wire anti-fraud gate evaluation and outbox event recording.

### Phase 4: REST API & Exposure
- [ ] `TASK-4.1` [RED]: Write MockMvc test verifying HTTP status codes, headers, and validations.
- [ ] `TASK-4.2` [GREEN]: Implement controller endpoint and exception handling.

---

## 3. Convergence & Verification Checklist

- [ ] All unit tests pass: `./gradlew test`
- [ ] All integration tests pass: Testcontainers suite green
- [ ] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`)
- [ ] Zero compiler / linter warnings
- [ ] OpenTelemetry traces verified in OpenObserve
- [ ] Seed data added/updated in `docker/init/schema.sql` (if new tables/state introduced)
- [ ] Author Practical Verification Guide & Seed Data in `SUMMARY-XXX.md` (`I-SDD-002`)
- [ ] Traceability report generated: 100% of requirements verified

