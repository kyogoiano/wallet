# 📊 Implementation Summary: SPEC-000.11 — Edge-to-Core Command Reliability, Retry & Reprocessing

- **Associated Spec**: [`../SPEC-000.11-tiered-dlq-and-recovery.md`](file:///.spec/SPEC-000.11-tiered-dlq-and-recovery.md)
- **Associated Plan**: [`../plans/PLAN-000.11-tiered-dlq-and-recovery.md`](file:///.spec/plans/PLAN-000.11-tiered-dlq-and-recovery.md)
- **Associated Tasks**: [`../tasks/TASKS-000.11-tiered-dlq-and-recovery.md`](file:///.spec/tasks/TASKS-000.11-tiered-dlq-and-recovery.md)
- **Governing Skills**:
  - [`capability-driven-development`](file:///.agents/skills/capability-driven-development/SKILL.md) (Modulith Capabilities & Lifecycle)
  - [`financial-cryptographic-security`](file:///.agents/skills/financial-cryptographic-security/SKILL.md) (Envelope Encryption & AEAD Isolation)
  - [`outbox-messaging`](file:///.agents/skills/outbox-messaging/SKILL.md) (Transactional Outbox & DLQ Handling)
  - [`spec-driven-development`](file:///.agents/skills/spec-driven-development/SKILL.md) (Spec Kit Orchestrator)
- **Status**: ✅ **Implemented & Verified**
- **Date**: 2026-10-03
- **Author**: Antigravity Platform Reliability & Financial Systems Guild

---

## 1. Executive Summary & Architectural Delivery

Phase 000.11 establishes a production-grade, tiered Dead Letter Queue (DLQ) and reprocessing framework for commands arriving from the Edge Gateway to Core Banking services. Following the **Uber Reliable Reprocessing model**, commands that fail ingestion or execution leave the live NATS JetStream ingress immediately before retries begin, preventing head-of-line blocking while guaranteeing zero lost messages and zero plaintext financial leaks:

1. **Durable Handoff Before ACK (`I-TDLQ-009`, `I-TDLQ-002`, `TASK-4.1`, `TASK-4.2`)**:
   - Enforced the mathematical invariant:
     $$\text{ACK}(m) \implies \text{DurableHandoff}(m) = \text{committed}$$
   - Updated [`CoreCommandConsumer`](file:///src/main/java/br/com/wallet/infrastructure/messaging/consumer/CoreCommandConsumer.java) to record failed commands into [`DlqManagementUseCase.recordFailure`](file:///src/main/java/br/com/wallet/dlq/api/DlqManagementUseCase.java) **before** calling `message.ack()`.
   - If PostgreSQL persistence throws or times out, the consumer executes `message.nakWithDelay(Duration.ofSeconds(5))` and suppresses ACK, guaranteeing that NATS redelivery protects against lost messages.

2. **Deterministic Failure Classification (`REQ-TDLQ-001`, `TASK-1.1`, `TASK-1.2`)**:
   - Implemented [`FailureClassifier`](file:///src/main/java/br/com/wallet/dlq/api/FailureClassifier.java) in `br.com.wallet.dlq.api` with causal chain unwrapping and defensive fallback.
   - Maps raw exceptions to four canonical architectural failure categories:
     - `TRANSIENT`: Lock contention, database connection timeouts, NATS timeouts, KMS transient outages.
     - `PERMANENT`: Business invariant rejections, account blocked/frozen states, insufficient funds, tenant mismatches.
     - `POISON`: Malformed envelope framing, invalid JSON syntax, schema/deserialization incompatibilities.
     - `SECURITY`: Cryptographic AEAD tag mismatches, nonce replay attacks, publisher identity fraud.

3. **Bounded Full-Jitter Exponential Backoff (`REQ-TDLQ-005`, `I-TDLQ-003`, `TASK-1.3`, `TASK-1.4`, `TASK-3.1`, `TASK-3.2`)**:
   - Implemented pure deterministic [`FullJitterBackoffCalculator`](file:///src/main/java/br/com/wallet/dlq/internal/engine/FullJitterBackoffCalculator.java):
     $$\Delta t_r = \text{Uniform}\left(0, \min(60\text{s}, 2\text{s} \times 2^r)\right)$$
   - Integrated into [`DlqOperationsDao`](file:///src/main/java/br/com/wallet/dlq/internal/persistence/DlqOperationsDao.java) and [`DlqReplayEngine`](file:///src/main/java/br/com/wallet/dlq/internal/engine/DlqReplayEngine.java).
   - Enforces a strict 3-retry cap ($r \in \{0, 1, 2\}$); on the 3rd failed reprocess attempt, the operation transitions irreversibly to `EXHAUSTED` and clears `next_retry_at`.

4. **Terminal State Disjunction (`REQ-TDLQ-004`, `I-TDLQ-004`, `TASK-1.5`, `TASK-2.1`)**:
   - Extended [`DlqStatus`](file:///src/main/java/br/com/wallet/dlq/api/model/DlqStatus.java) with `QUARANTINED` and lifecycle transition validation methods (`isAutomatedRetryEligible()`, `isTerminal()`, `canTransitionTo()`).
   - Non-transient errors (`PERMANENT`, `POISON`, `SECURITY`) route immediately to `QUARANTINED` status with `retry_count = 0` and `next_retry_at = NULL`.
   - Automated replay workers claim strictly `WHERE status IN ('PENDING', 'FAILED') AND retry_count < 3`, ignoring both `EXHAUSTED` and `QUARANTINED` records.

5. **Financial Identity Preservation (`REQ-TDLQ-006`, `I-TDLQ-005`, `TASK-3.3`)**:
   - All automated and manual replays preserve the immutable financial `operation_id` across database, headers, and outbox logs.
   - Replays generate a newly minted operational identifier `replay_id: UUID`, flag `replayed: true`, and propagate `tenant_id` in canonical lowercase `snake_case`. Duplicate `X-` headers (`X-Replay-Id`, `X-Replayed`, `X-Tenant-Id`) were eliminated.
   - Client nonces (`X-Nonce`) are never reused on replayed messages, satisfying replay protection constraints.

6. **Cryptographic Opacity (`REQ-TDLQ-007`, `I-TDLQ-006`, `TASK-2.3`)**:
   - Payloads stored in `dlq_operations.payload` are the raw serialized bytes of the [`CryptoEnvelope`](file:///core/src/main/java/br/com/wallet/security/envelope/CryptoEnvelope.java).
   - Zero plaintext financial amounts, account numbers, or data encryption keys are logged or persisted in DLQ tables, MDC, or telemetry (`I-SEC-012`).

7. **Cryptographic Integrity Replay Prohibition (`REQ-TDLQ-009`, `I-TDLQ-007`, `TASK-5.3`, `TASK-5.4`)**:
   - Created [`NonReplayableOperationException`](file:///src/main/java/br/com/wallet/dlq/api/exceptions/NonReplayableOperationException.java) annotated with `@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)`.
   - Updated [`DlqManagementService.replayOperation`](file:///src/main/java/br/com/wallet/dlq/internal/service/DlqManagementService.java): attempting to replay an operation whose failure was caused by an AEAD tag mismatch immediately rejects with HTTP 422, preventing tampered ciphertext re-execution.

8. **Operator Governance REST API & Multitenancy (`REQ-TDLQ-008`, `REQ-TDLQ-010`, `TASK-5.1`, `TASK-5.2`, `TASK-5.5`)**:
   - Updated [`DlqApi`](file:///src/main/java/br/com/wallet/infrastructure/rest/api/DlqApi.java) and [`DlqController`](file:///src/main/java/br/com/wallet/infrastructure/rest/controller/DlqController.java) to accept `tenantId`, `status` (`QUARANTINED`, `EXHAUSTED`), and `failureType` filters.
   - Updated schema migrations in `docker/init/schema.sql`, `src/main/resources/schema.sql`, and `src/test/resources/schema.sql`:
     - Added column `tenant_id VARCHAR(64) NOT NULL` strictly without legacy `DEFAULT 'default'` shim (`I-SDD-007`).
     - Check constraints: `chk_dlq_status` and `chk_dlq_failure_type` (`TRANSIENT`, `PERMANENT`, `POISON`, `SECURITY`), eliminating legacy `BUSINESS` enum value.
     - Added claim index `idx_dlq_operations_claim` on `(status, next_retry_at)` and tenant query index `idx_dlq_operations_tenant_query` on `(tenant_id, status, failure_type, created_at DESC)`.

---

## 2. Traceability Matrix & Zero Spec-Drift Reconciliation (`I-SDD-003`)

| Requirement / Invariant | Status | Primary Implementation Symbol | Verification Test |
| :--- | :---: | :--- | :--- |
| `REQ-TDLQ-001` (Failure Classification) | ✅ | [`FailureClassifier.classify()`](file:///src/main/java/br/com/wallet/dlq/api/FailureClassifier.java) | [`FailureClassifierTest`](file:///src/test/java/br/com/wallet/unit/dlq/FailureClassifierTest.java) |
| `REQ-TDLQ-002` (Ingress Unblocking) | ✅ | [`CoreCommandConsumer.handlePoisonMessage()`](file:///src/main/java/br/com/wallet/infrastructure/messaging/consumer/CoreCommandConsumer.java) | [`CoreCommandConsumerHandoffTest`](file:///src/test/java/br/com/wallet/unit/infrastructure/messaging/CoreCommandConsumerHandoffTest.java) |
| `REQ-TDLQ-003` (Edge Admission Non-Persistence)| ✅ | [`EdgeAdmissionSecurityIT`](file:///src/test/java/br/com/wallet/integration/dlq/EdgeAdmissionSecurityIT.java) | [`EdgeAdmissionSecurityIT`](file:///src/test/java/br/com/wallet/integration/dlq/EdgeAdmissionSecurityIT.java) |
| `REQ-TDLQ-004` (Immediate Quarantine) | ✅ | [`DlqOperationsDao.recordQuarantined()`](file:///src/main/java/br/com/wallet/dlq/internal/persistence/DlqOperationsDao.java) | [`DlqOperationsDaoIT`](file:///src/test/java/br/com/wallet/integration/dlq/DlqOperationsDaoIT.java) |
| `REQ-TDLQ-005` (Bounded Full-Jitter Replay) | ✅ | [`FullJitterBackoffCalculator`](file:///src/main/java/br/com/wallet/dlq/internal/engine/FullJitterBackoffCalculator.java) | [`FullJitterBackoffCalculatorTest`](file:///src/test/java/br/com/wallet/unit/dlq/FullJitterBackoffCalculatorTest.java)<br/>[`DlqReplayEngineIT`](file:///src/test/java/br/com/wallet/integration/dlq/DlqReplayEngineIT.java) |
| `REQ-TDLQ-006` (Identity Preservation) | ✅ | [`DlqReplayEngine.replay()`](file:///src/main/java/br/com/wallet/dlq/internal/engine/DlqReplayEngine.java) | [`DlqReplayIdentityTest`](file:///src/test/java/br/com/wallet/unit/dlq/DlqReplayIdentityTest.java) |
| `REQ-TDLQ-007` (Cryptographic Opacity) | ✅ | [`DlqEncryptedPayloadTest`](file:///src/test/java/br/com/wallet/unit/dlq/DlqEncryptedPayloadTest.java) | [`DlqEncryptedPayloadTest`](file:///src/test/java/br/com/wallet/unit/dlq/DlqEncryptedPayloadTest.java) |
| `REQ-TDLQ-008` (Operator Query API) | ✅ | [`DlqController.listOperations()`](file:///src/main/java/br/com/wallet/infrastructure/rest/controller/DlqController.java) | [`DlqControllerTest`](file:///src/test/java/br/com/wallet/unit/infrastructure/rest/DlqControllerTest.java) |
| `REQ-TDLQ-009` (Guarded Manual Replay) | ✅ | [`DlqManagementService.replayOperation()`](file:///src/main/java/br/com/wallet/dlq/internal/service/DlqManagementService.java) | [`DlqManagementServiceTest`](file:///src/test/java/br/com/wallet/unit/dlq/service/DlqManagementServiceTest.java) |
| `REQ-TDLQ-010` (Operator Discard) | ✅ | [`DlqManagementService.discardOperation()`](file:///src/main/java/br/com/wallet/dlq/internal/service/DlqManagementService.java) | [`DlqControllerTest`](file:///src/test/java/br/com/wallet/unit/infrastructure/rest/DlqControllerTest.java) |
| `I-TDLQ-001` (Live Ingress Isolation) | ✅ | [`CoreCommandConsumer`](file:///src/main/java/br/com/wallet/infrastructure/messaging/consumer/CoreCommandConsumer.java) | [`CoreCommandConsumerHandoffTest`](file:///src/test/java/br/com/wallet/unit/infrastructure/messaging/CoreCommandConsumerHandoffTest.java) |
| `I-TDLQ-002` (Live Unblocking) | ✅ | [`CoreCommandConsumer`](file:///src/main/java/br/com/wallet/infrastructure/messaging/consumer/CoreCommandConsumer.java) | [`CoreCommandConsumerHandoffTest`](file:///src/test/java/br/com/wallet/unit/infrastructure/messaging/CoreCommandConsumerHandoffTest.java) |
| `I-TDLQ-003` (Bounded Jittered Retries) | ✅ | [`FullJitterBackoffCalculator`](file:///src/main/java/br/com/wallet/dlq/internal/engine/FullJitterBackoffCalculator.java) | [`FullJitterBackoffCalculatorTest`](file:///src/test/java/br/com/wallet/unit/dlq/FullJitterBackoffCalculatorTest.java) |
| `I-TDLQ-004` (Terminal State Disjunction) | ✅ | [`DlqStatus`](file:///src/main/java/br/com/wallet/dlq/api/model/DlqStatus.java) | [`DlqStateTransitionTest`](file:///src/test/java/br/com/wallet/unit/dlq/DlqStateTransitionTest.java) |
| `I-TDLQ-005` (Financial Identity Preservation)| ✅ | [`DlqReplayEngine`](file:///src/main/java/br/com/wallet/dlq/internal/engine/DlqReplayEngine.java) | [`DlqReplayIdentityTest`](file:///src/test/java/br/com/wallet/unit/dlq/DlqReplayIdentityTest.java) |
| `I-TDLQ-006` (Cryptographic Opacity) | ✅ | [`DlqOperationsDaoIT.shouldAssertZeroPlaintextInDatabase()`](file:///src/test/java/br/com/wallet/integration/dlq/DlqOperationsDaoIT.java) | [`DlqOperationsDaoIT`](file:///src/test/java/br/com/wallet/integration/dlq/DlqOperationsDaoIT.java) |
| `I-TDLQ-007` (Crypto Integrity Non-Replayable)| ✅ | [`NonReplayableOperationException`](file:///src/main/java/br/com/wallet/dlq/api/exceptions/NonReplayableOperationException.java) | [`DlqControllerTest`](file:///src/test/java/br/com/wallet/unit/infrastructure/rest/DlqControllerTest.java) |
| `I-TDLQ-008` (Admission Non-Persistence) | ✅ | [`EdgeAdmissionSecurityIT`](file:///src/test/java/br/com/wallet/integration/dlq/EdgeAdmissionSecurityIT.java) | [`EdgeAdmissionSecurityIT`](file:///src/test/java/br/com/wallet/integration/dlq/EdgeAdmissionSecurityIT.java) |
| `I-TDLQ-009` (Durable Handoff Before ACK) | ✅ | [`CoreCommandConsumer.handlePoisonMessage()`](file:///src/main/java/br/com/wallet/infrastructure/messaging/consumer/CoreCommandConsumer.java) | [`CoreCommandConsumerHandoffTest`](file:///src/test/java/br/com/wallet/unit/infrastructure/messaging/CoreCommandConsumerHandoffTest.java) |

---

## 3. Practical Verification Guide (`I-SDD-002`)

### 3.1 Operator REST Verification Commands

```bash
# 1. Inspect Quarantined Operations for Tenant "tenant-finance"
curl -s -X GET "http://localhost:8081/dlq/operations?status=QUARANTINED&tenantId=tenant-finance&limit=10" \
  -H "Accept: application/json" | jq .

# 2. Inspect Exhausted Transient Operations
curl -s -X GET "http://localhost:8081/dlq/operations?status=EXHAUSTED&limit=5" \
  -H "Accept: application/json" | jq .

# 3. Manually Replay an Exhausted Operation
# (Preserves original operation_id, attaches new replay_id and tenant_id)
curl -s -X POST "http://localhost:8081/dlq/operations/a1b2c3d4-e5f6-7890-abcd-ef1234567890/replay" \
  -H "Accept: application/json" | jq .

# 4. Attempt Replay of Cryptographically Tampered Message (AEAD Tag Mismatch)
# Expected Response: HTTP 422 Unprocessable Entity
curl -s -i -X POST "http://localhost:8081/dlq/operations/b2c3d4e5-f6a7-8901-bcde-f12345678901/replay" \
  -H "Accept: application/json"
# HTTP/1.1 422 Unprocessable Entity
# {"code":"wallet.non_replayable_operation","message":"Cryptographically corrupted ciphertext (AEAD tag mismatch) cannot be replayed"}

# 5. Operator Discard with Audit Reason
curl -s -X POST "http://localhost:8081/dlq/operations/c3d4e5f6-a7b8-9012-cdef-123456789012/discard" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Investigated by FinCrime - account confirmed fraudulent"}' | jq .
```

### 3.2 Direct PostgreSQL State Validation Queries

```sql
-- Verify Terminal State Disjunction (EXHAUSTED vs QUARANTINED)
SELECT status, failure_type, retry_count, count(*)
FROM dlq_operations
GROUP BY status, failure_type, retry_count
ORDER BY status;

-- Verify Bounded Retries (Never exceeds 3 for automated retries)
SELECT id, operation_id, retry_count, status, next_retry_at
FROM dlq_operations
WHERE status IN ('PENDING', 'FAILED')
  AND retry_count >= 3; -- Expected: 0 rows (all must be EXHAUSTED)

-- Verify Cryptographic Opacity in Storage (Zero plaintext financial attributes)
SELECT count(*) AS plaintext_leaks
FROM dlq_operations
WHERE payload::text ILIKE '%"amount"%'
   OR payload::text ILIKE '%sourceAccountId%'; -- Expected: 0 leaks for encrypted commands
```

---

## 4. Next Phase Handoff: SPEC-000.12

With Edge-to-Core command reprocessing, unblocking, and quarantine fully sealed and verified, the platform is prepared for:

👉 **[Phase 000.12 — Modulith Ingress Decentralization & Intra-Core Event Alignment](file:///.spec/SPEC-000.12-modulith-bounded-context-streaming.md)**:
1. **Monolith Ingress Decentralization**: Decompose monolithic [`CoreCommandConsumer`](file:///src/main/java/br/com/wallet/infrastructure/messaging/consumer/CoreCommandConsumer.java) into cohesive capability consumers (`TransferCommandConsumer`, `DepositCommandConsumer`, `WithdrawCommandConsumer`) under `br.com.wallet.ledger.internal.messaging.consumer`.
2. **Intra-Core Event Alignment**: Migrate intra-Core domain event listeners (`FraudGraphConsumer`, `FraudConsumer`) from NATS JetStream boomerangs to native Spring Modulith `@ApplicationModuleListener` in-process asynchronous events.
3. **NATS Process Boundary Demarcation**: Demarcate NATS strictly for process boundary crossings (Edge $\to$ Core ingress commands and Outbox Relay egress to external systems).
