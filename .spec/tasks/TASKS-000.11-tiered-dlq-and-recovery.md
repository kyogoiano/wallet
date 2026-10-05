# 📝 Task Breakdown: TASKS-000.11 — Edge-to-Core Command Reliability, Retry & Reprocessing

- **Associated Spec**: [`../SPEC-000.11-tiered-dlq-and-recovery.md`](file:///.spec/SPEC-000.11-tiered-dlq-and-recovery.md)
- **Associated Plan**: [`../plans/PLAN-000.11-tiered-dlq-and-recovery.md`](file:///.spec/plans/PLAN-000.11-tiered-dlq-and-recovery.md)
- **Status**: Completed
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-TDLQ-001` (Failure Classification) | `[MUST]` | `FailureClassifierTest.shouldClassifyExceptionsCorrectly()` | `TASK-1.1`, `TASK-1.2` |
| `REQ-TDLQ-002` (Ingress Unblocking) | `[MUST]` | `CoreCommandConsumerHandoffTest.shouldCommitToDlqAndAckNats()` | `TASK-4.1`, `TASK-4.2` |
| `REQ-TDLQ-003` (Edge Admission Non-Persistence)| `[MUST]` | `EdgeAdmissionSecurityIT.shouldRejectWithoutDlqRecord()` | `TASK-4.3` |
| `REQ-TDLQ-004` (Immediate Quarantine) | `[MUST]` | `DlqOperationsDaoIT.shouldQuarantineWithZeroRetries()` | `TASK-2.1`, `TASK-2.2` |
| `REQ-TDLQ-005` (Bounded Full-Jitter Replay) | `[MUST]` | `FullJitterBackoffCalculatorTest.shouldCalculateDecorrelatedJitter()`<br/>`DlqReplayEngineIT.shouldTransitionToExhaustedAfter3Retries()` | `TASK-1.3`, `TASK-1.4`<br/>`TASK-3.1`, `TASK-3.2` |
| `REQ-TDLQ-006` (Identity Preservation) | `[MUST]` | `DlqReplayIdentityTest.shouldPreserveOperationIdAndAttachReplayId()` | `TASK-3.3` |
| `REQ-TDLQ-007` (Cryptographic Opacity) | `[MUST]` | `DlqEncryptedPayloadTest.shouldPreserveCryptoEnvelopeOpacityInDlqPayload()` | `TASK-2.3` |
| `REQ-TDLQ-008` (Operator Query API) | `[MUST]` | `DlqControllerTest.shouldFilterByFailureTypeAndTenant()` | `TASK-5.1`, `TASK-5.2` |
| `REQ-TDLQ-009` (Guarded Manual Replay) | `[MUST]` | `DlqControllerTest.shouldRejectReplayOfAeadTagMismatchWith422()` | `TASK-5.3`, `TASK-5.4` |
| `REQ-TDLQ-010` (Operator Discard) | `[MUST]` | `DlqControllerTest.shouldDiscardOperationWithReason()` | `TASK-5.5` |
| `I-TDLQ-001` (Live Ingress Isolation) | `[MUST]` | `CoreCommandConsumerHandoffTest.shouldNotBlockLiveIngressOnFailure()` | `TASK-4.1` |
| `I-TDLQ-002` (Live Unblocking) | `[MUST]` | `CoreCommandConsumerHandoffTest.shouldAckLiveMessageAfterDlqCommit()` | `TASK-4.2` |
| `I-TDLQ-003` (Bounded Jittered Retries) | `[MUST]` | `FullJitterBackoffCalculatorTest.shouldStayWithinBounds()` | `TASK-1.3` |
| `I-TDLQ-004` (Terminal State Disjunction) | `[MUST]` | `DlqStateTransitionTest.shouldDistinguishExhaustedFromQuarantined()`| `TASK-1.5` |
| `I-TDLQ-005` (Financial Identity Preservation)| `[MUST]` | `DlqReplayIdentityTest.shouldKeepOperationIdImmutable()` | `TASK-3.3` |
| `I-TDLQ-006` (Cryptographic Opacity) | `[MUST]` | `DlqOperationsDaoIT.shouldAssertZeroPlaintextInDatabase()` | `TASK-2.3` |
| `I-TDLQ-007` (Crypto Integrity Non-Replayable)| `[MUST]` | `DlqManagementServiceTest.shouldProhibitAeadMismatchReplay()` | `TASK-5.3` |
| `I-TDLQ-008` (Admission Non-Persistence) | `[MUST]` | `EdgeAdmissionSecurityIT.shouldAssertZeroDlqRecordsOnAuthFailure()` | `TASK-4.3` |
| `I-TDLQ-009` (Durable Handoff Before ACK) | `[MUST]` | `CoreCommandConsumerHandoffTest.shouldNotAckNatsIfDbFails()` | `TASK-4.2` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing an atomic task, isolate working focus to the target card. Never load unrelated module files into memory.

```markdown
### 🎯 Active Task Card: TASK-X.Y
- **Target Invariant**: I-TDLQ-00X
- **Target Requirement**: REQ-TDLQ-00X [MUST]
- **Target Files**: <TargetClass>.java, <TargetClassTest>.java
- **In-Scope Contracts**: Inputs -> TargetDTO, Outputs -> ExpectedResult
- **Forbidden Boundary**: Do not modify unrelated modules (e.g. savings, goals).
```

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Failure Classification & Domain Models ([MUST])
- [x] `TASK-1.1` [RED]: Write unit tests in `FailureClassifierTest` asserting mapping of `LockAcquisitionException`/`TransientException` $\rightarrow$ `TRANSIENT`, `AccountBlockedException`/`InsufficientFundsException` $\rightarrow$ `PERMANENT`, `JsonParseException`/`DeserializationException` $\rightarrow$ `POISON`, and `CryptographicIntegrityException` $\rightarrow$ `SECURITY`.
- [x] `TASK-1.2` [GREEN]: Implement `FailureClassifier` in `br.com.wallet.dlq.api`. Update `DlqFailureType` enum to include `TRANSIENT`, `PERMANENT`, `POISON`, `SECURITY`.
- [x] `TASK-1.3` [RED]: Write unit tests in `FullJitterBackoffCalculatorTest` asserting $\Delta t_r = \text{Uniform}(0, \min(60\text{s}, 2\text{s} \times 2^r))$ for $r \in \{0, 1, 2, 3\}$.
- [x] `TASK-1.4` [GREEN]: Implement pure deterministic `FullJitterBackoffCalculator` in `br.com.wallet.dlq.internal.engine`.
- [x] `TASK-1.5` [RED/GREEN]: Update `DlqStatus` enum to add `QUARANTINED`. Write `DlqStateTransitionTest` validating allowed transitions and asserting terminal disjunction `EXHAUSTED != QUARANTINED` (`I-TDLQ-004`).

### Phase 2: PostgreSQL Schema Migrations & Persistence ([MUST])
- [x] `TASK-2.1` [RED]: Write Testcontainers integration test in `DlqOperationsDaoIT` testing `recordQuarantined` (status `QUARANTINED`, `retry_count=0`, `next_retry_at=null`) and `markFailed` (transient retry with jitter).
- [x] `TASK-2.2` [GREEN]: Update `docker/init/schema.sql` check constraints:
  - `chk_dlq_status`: `('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXHAUSTED', 'QUARANTINED', 'DISCARDED')`.
  - `chk_dlq_failure_type`: `('TRANSIENT', 'PERMANENT', 'POISON', 'SECURITY')`.
  - Indexes: `idx_dlq_operations_claim` on `(status, next_retry_at)` and `idx_dlq_operations_tenant_query` on `(tenant_id, status, failure_type, created_at DESC)`.
  - Update `DlqOperationsDao` to support `QUARANTINED` records and jittered `next_retry_at`.
- [x] `TASK-2.3` [RED/GREEN]: Implement `DlqOperationsDaoIT.shouldAssertZeroPlaintextInDatabase()` asserting zero plaintext financial command JSON in PostgreSQL (`I-TDLQ-006`) and `DlqEncryptedPayloadTest` asserting `CryptoEnvelope` opacity and AES-GCM roundtrip.

### Phase 3: Bounded Replay Engine & Identity Preservation ([MUST])
- [x] `TASK-3.1` [RED]: Write integration test in `DlqReplayEngineIT` verifying:
  - Batch claiming via `claimBatch(now, limit)` uses `FOR UPDATE SKIP LOCKED`.
  - Transient records retried up to 3 times before transitioning irreversibly to `EXHAUSTED`.
  - Quarantined records are ignored by automated batch claim.
- [x] `TASK-3.2` [GREEN]: Update `DlqReplayEngine` to integrate `FullJitterBackoffCalculator`, enforce the 3-retry cap, and transition to `EXHAUSTED`.
- [x] `TASK-3.3` [RED/GREEN]: Implement `DlqReplayIdentityTest` asserting that replayed messages preserve `original_operation_id`, attach a newly generated `replay_id = UUID.randomUUID()`, propagate `replayed=true`, and do not reuse the client nonce (`I-TDLQ-005`).

### Phase 4: Ingress Consumer Integration & Durable Handoff ([MUST])
- [x] `TASK-4.1` [RED]: Write unit test in `CoreCommandConsumerHandoffTest` asserting that when an ingress command fails, live NATS command processing continues unimpeded for subsequent commands (`I-TDLQ-001`).
- [x] `TASK-4.2` [GREEN]: Enforce Durable Handoff Before ACK (`I-TDLQ-009`, `I-TDLQ-002`): update `CoreCommandConsumer` (and future capability consumers) so that upon failure, the command is committed to `dlq_operations` via `dlqManagementUseCase.recordFailure(...)` **before** invoking `msg.ack()`. If DB persistence throws, message is not ACKed.
- [x] `TASK-4.3` [RED/GREEN]: Implement `EdgeAdmissionSecurityIT` verifying that unauthenticated Edge requests (invalid HMAC signature, timestamp drift, duplicate nonce) return HTTP 401/400 and create 0 DLQ database records (`I-TDLQ-008`).

### Phase 5: Operator Governance API & Crypto Integrity Protection ([MUST])
- [x] `TASK-5.1` [RED]: Write MockMvc tests in `DlqControllerTest` for `GET /dlq/operations` with filtering by `failureType` (`TRANSIENT`, `PERMANENT`, `POISON`, `SECURITY`), `tenantId`, and status (`QUARANTINED`, `EXHAUSTED`).
- [x] `TASK-5.2` [GREEN]: Update `DlqQueryUseCase`, `DlqQueryService`, and `DlqController` to support the updated filter criteria and sanitized response DTOs (`I-SEC-012`).
- [x] `TASK-5.3` [RED]: Write unit/integration tests asserting that `POST /dlq/operations/{id}/replay` on an operation with an AEAD tag verification failure throws `NonReplayableOperationException` and returns `HTTP 422 UNPROCESSABLE ENTITY` (`I-TDLQ-007`).
- [x] `TASK-5.4` [GREEN]: Implement replay guard in `DlqManagementService.replayOperation`: verify cryptographic integrity, preserve `operationId`, generate `replayId`, transition to `PENDING` (or immediately republish), and record operator audit log.
- [x] `TASK-5.5` [RED/GREEN]: Write tests and implement `POST /dlq/operations/{id}/discard` requiring an operator audit reason and transitioning status to `DISCARDED`.

### Phase 6: Observability & Resilience Polish ([SHOULD] / [COULD])
- [x] `TASK-6.1` [SHOULD]: Add OpenTelemetry spans (`dlq.record_failure`, `dlq.claim_batch`, `dlq.auto_replay`, `dlq.manual_replay`) with baggage propagation and zero PII/plaintext leakage (`I-SEC-012`).
- [x] `TASK-6.2` [COULD]: Add pagination and sorting ergonomics to `GET /dlq/operations`.

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [ ] All unit tests pass: `./gradlew test`
- [ ] All integration tests pass: Testcontainers suite green
- [ ] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [ ] Zero compiler / linter warnings
- [ ] OpenTelemetry traces verified
- [ ] **Zero Spec-Drift Reconciliation (`I-SDD-003`)**: Class names, package paths, and DDL schemas in `SPEC-000.11` and `PLAN-000.11` match `src/` 100%
- [ ] All task checkboxes in `TASKS-000.11-tiered-dlq-and-recovery.md` marked `[x]`
- [ ] Author Practical Verification Guide & Seed Data in `SUMMARY-000.11-tiered-dlq-and-recovery.md` (`I-SDD-002`)
