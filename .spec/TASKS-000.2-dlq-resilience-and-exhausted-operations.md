# 📝 Task Breakdown: TASKS-000.2 — DLQ Resilience, EXHAUSTED Status & Spring Modulith Isolation

- **Associated Spec**: [`SPEC-000.2-dlq-resilience-and-exhausted-operations.md`](file:///.spec/SPEC-000.2-dlq-resilience-and-exhausted-operations.md)
- **Associated Plan**: [`PLAN-000.2-dlq-resilience-and-exhausted-operations.md`](file:///.spec/PLAN-000.2-dlq-resilience-and-exhausted-operations.md)
- **Status**: In Progress

---

## 1. Traceability Matrix

| Requirement / Invariant | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-DLQ-001`, `I-DLQ-004` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-DLQ-002`, `I-DLQ-002` | `DlqStatusTest` / `DlqManagementServiceTest` | `TASK-1.2` |
| `REQ-DLQ-003`, `REQ-DLQ-004`, `I-DLQ-001` | `DlqOperationsDaoIT.shouldTransitionToExhaustedAfter3Retries()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-DLQ-005`, `I-DLQ-003` | `DlqManagementServiceTest.shouldReplayExhaustedOperation()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-DLQ-006` | `DlqManagementServiceTest.shouldDiscardOperation()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-DLQ-007` | `DlqQueryServiceTest.shouldQueryByStatusAndFilters()` | `TASK-3.1`, `TASK-3.2` |
| `REQ-DLQ-008` | `DlqControllerTest` (MockMvc) & `DlqRestIT` | `TASK-4.1`, `TASK-4.2` |

---

## 2. Implementation Tasks (TDD Order)

### Phase 1: Modulith Package Structure & Domain Models
- [ ] `TASK-1.1`: Create `br.com.wallet.dlq` module structure: `package-info.java`, `api/package-info.java`, `api/model/*` (`DlqEvent`, `DlqStatus`, `DlqFailureType`), `api/dto/*` (`DlqOperationResponse`, `DlqQueryFilter`, `DiscardDlqCommand`).
- [ ] `TASK-1.2`: Update `DlqStatus` enum with `EXHAUSTED` and `DISCARDED`.
- [ ] `TASK-1.3`: Define Use Case interfaces in `br.com.wallet.dlq.api`: `DlqManagementUseCase` and `DlqQueryUseCase`.

### Phase 2: Schema & Persistence
- [ ] `TASK-2.1`: Update `docker/init/schema.sql` `dlq_operations` check constraint with `EXHAUSTED` and `DISCARDED`.
- [ ] `TASK-2.2`: Implement/Relocate `DlqOperationsDao` to `br.com.wallet.dlq.internal.persistence` with 3-retry cap logic (`retry_count < 3` and transition to `EXHAUSTED`).
- [ ] `TASK-2.3`: Write/Update `DlqOperationsDaoIT` to verify automatic transition to `EXHAUSTED` on 3rd failure.

### Phase 3: Application Services & Replay Engine
- [ ] `TASK-3.1` [RED]: Write unit tests for `DlqManagementService`, `DlqQueryService`, and updated `DlqReplayEngine`.
- [ ] `TASK-3.2` [GREEN]: Implement `DlqManagementService`, `DlqQueryService`, and relocate `DlqReplayEngine` to `br.com.wallet.dlq.internal.engine`.

### Phase 4: REST API Exposure & Infrastructure Integration
- [ ] `TASK-4.1` [RED]: Write MockMvc tests `DlqControllerTest` for `/dlq/operations` endpoints.
- [ ] `TASK-4.2` [GREEN]: Implement `DlqApi` and `DlqController` in `br.com.wallet.infrastructure.rest`.
- [ ] `TASK-4.3`: Update `DlqConsumer` and `DlqPublisher` to reference `br.com.wallet.dlq.api`.
- [ ] `TASK-4.4`: Update `infrastructure/package-info.java` allowedDependencies.

### Phase 5: Verification & Post-Mortem Summary
- [ ] `TASK-5.1`: Verify Spring Modulith architectural boundaries (`ModulithArchitectureTest`).
- [ ] `TASK-5.2`: Run full test suite (`./gradlew test`).
- [ ] `TASK-5.3`: Author `SUMMARY-000.2-dlq-resilience-and-exhausted-operations.md`.

---

## 3. Convergence & Verification Checklist

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Zero Spring Modulith boundary violations
- [ ] Traceability report generated: 100% of requirements verified
