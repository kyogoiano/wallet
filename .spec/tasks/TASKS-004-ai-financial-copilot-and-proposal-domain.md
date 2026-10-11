# 📝 Task Breakdown: TASKS-004 — AI Financial Copilot & Proposal Domain

- **Associated Spec**: [`../SPEC-004-ai-financial-copilot-and-proposal-domain.md`](file:///.spec/SPEC-004-ai-financial-copilot-and-proposal-domain.md)
- **Associated Plan**: [`../plans/PLAN-004-ai-financial-copilot-and-proposal-domain.md`](file:///.spec/plans/PLAN-004-ai-financial-copilot-and-proposal-domain.md)
- **Associated Architecture**: [`../architecture/ARCH-004-ai-financial-copilot-and-mcp-gateway.md`](file:///.spec/architecture/ARCH-004-ai-financial-copilot-and-mcp-gateway.md)
- **Status**: 📝 Approved / Ready for Execution (Refined per History 101)
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until all `[MUST]` criteria are green (`I-SDD-004`).
- **Atomic Spec Slicing Scope**: Max 250 lines (`I-SDD-006`). Adapter/MCP layer sliced into `SPEC-004.1`.

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test / Artifact | Task IDs |
| :--- | :---: | :--- | :--- |
| `REQ-COPILOT-001` (Modulith boundary & packaging) | `[MUST]` | `ModulithArchitectureTest.verifyArchitecture()` | `TASK-4.1`, `TASK-4.14` |
| `REQ-COPILOT-002` (`FinancialProposal` entity & DDL) | `[MUST]` | `ProposalDaoIT`, `ProposalStateMachineTest` | `TASK-4.2`, `TASK-4.4`, `TASK-4.5` |
| `REQ-COPILOT-003` (Proposal Creation Idempotency) | `[MUST]` | `ProposalServiceTest`, `ProposalDaoIT` | `TASK-4.3`, `TASK-4.5`, `TASK-4.7` |
| `REQ-COPILOT-004` (State machine transitions) | `[MUST]` | `ProposalStateMachineTest`, `ProposalServiceTest` | `TASK-4.2`, `TASK-4.8` |
| `REQ-COPILOT-005` (Atomic Execution Claim with Lease) | `[MUST]` | `ProposalConcurrentApprovalIT`, `ProposalDaoIT` | `TASK-4.5`, `TASK-4.8`, `TASK-4.12` |
| `REQ-COPILOT-006` (Synchronous Authoritative TTL Gate) | `[MUST]` | `ProposalServiceTest` | `TASK-4.8` |
| `REQ-COPILOT-007` (Human Authorization REST API) | `[MUST]` | `ProposalControllerTest` | `TASK-4.10`, `TASK-4.11` |
| `REQ-COPILOT-008` (Execution Idempotency & Cached Ref) | `[MUST]` | `ProposalServiceTest`, `ProposalExecutionIT` | `TASK-4.8`, `TASK-4.12` |
| `REQ-COPILOT-009` (Precondition: Business vs Tech Failure) | `[MUST]` | `DownstreamExecutionBridgeTest` | `TASK-4.6` |
| `REQ-COPILOT-010` (Downstream Bridge Dispatch) | `[MUST]` | `DownstreamExecutionBridgeTest`, `ProposalExecutionIT` | `TASK-4.6`, `TASK-4.12` |
| `REQ-COPILOT-011` (Human Principal from SecurityContext) | `[MUST]` | `ProposalControllerTest` | `TASK-4.10`, `TASK-4.11` |
| `REQ-COPILOT-012` (Background TTL Sweeper Housekeeping) | `[SHOULD]` | `ProposalTtlSweeperTest` | `TASK-4.13` |
| `REQ-COPILOT-013` (Modulith Domain Events Emission) | `[SHOULD]` | `ProposalServiceTest` | `TASK-4.13` |
| `REQ-COPILOT-014` (Agent Rationale & Risk Metadata) | `[COULD]` | `ProposalServiceTest` | `TASK-4.13` |
| `I-AI-001` (Human Authorization Gate & Recovery Reuse) | `[MUST]` | `ProposalServiceTest`, `ProposalLeaseRecoveryIT` | `TASK-4.8`, `TASK-4.9`, `TASK-4.12` |
| `I-AI-004` (Authenticated Tenant & 404 Non-Disclosure) | `[MUST]` | `ProposalControllerTest` | `TASK-4.10`, `TASK-4.11` |
| `I-AI-005` (Atomic Claim Lease & Authoritative TTL Gate) | `[MUST]` | `ProposalConcurrentApprovalIT`, `ProposalDaoIT` | `TASK-4.5`, `TASK-4.8`, `TASK-4.12` |
| `I-AI-006` (Immutable `executionOperationId` Idempotency)| `[MUST]` | `ProposalServiceTest`, `ProposalExecutionIT` | `TASK-4.4`, `TASK-4.7`, `TASK-4.12` |
| `I-AI-007` (Parameters Snapshot Immutability SHA-256) | `[MUST]` | `ParametersHashUtilTest`, `ProposalDaoIT` | `TASK-4.3`, `TASK-4.5` |
| `I-AI-008` (Creation Idempotency & Conflict Gate) | `[MUST]` | `ProposalServiceTest`, `ProposalDaoIT` | `TASK-4.5`, `TASK-4.7` |
| `I-AI-009` (Precondition Boundary: Downstream Rules) | `[MUST]` | `ProposalExecutionIT` | `TASK-4.6`, `TASK-4.12` |
| `I-AI-010` (Business Invalidation vs Technical Lease) | `[MUST]` | `DownstreamExecutionBridgeTest`, `ProposalLeaseRecoveryIT` | `TASK-4.6`, `TASK-4.9`, `TASK-4.12` |

---

## 2. Active Task Card Protocol (Context Hygiene)

```markdown
### 🎯 Active Task Card: TASK-4.X
- **Target Invariants**: I-AI-001, I-AI-005, I-AI-006, I-AI-010
- **Target Requirements**: REQ-COPILOT-00X [MUST]
- **Target Files**: <TargetClass>.java, <TargetClassTest>.java
- **In-Scope Contracts**: Inputs -> CommandDTO/Parameters, Outputs -> FinancialProposal/Response
- **Forbidden Boundary**: Direct mutation of ledger/accounts tables; direct bypass of human authorization gate.
```

---

## 3. Implementation Tasks (TDD Order: Red $\to$ Green $\to$ Refactor)

### Phase 1: Modulith Packaging, Minimal API & Canonical Hash ([MUST])
- [x] `TASK-4.1` [GREEN]: Configure Modulith module packaging in `package-info.java` (`@ApplicationModule(allowedDependencies = {"ledger::api", "savings::api", "goals::api", "core::api"})`) and `@NamedInterface("api")` in `copilot.api` (`REQ-COPILOT-001`, `I-MODULITH-001`, `I-MODULITH-002`).
- [x] `TASK-4.2` [RED/GREEN]: Implement minimal public API contracts in `copilot.api` and domain models:
  - Public API (`copilot.api`): `ProposalUseCase`, `CreateProposalCommand`, `ApproveProposalCommand`, `RejectProposalCommand`, `ProposalResponse`, `ProposalStatus`, `ProposalType`.
  - Domain records in `copilot.internal.model` (internal encapsulation): `FinancialProposal` record holding all snapshot attributes.
  - State machine unit tests in `ProposalStateMachineTest` asserting allowed and forbidden transitions (`REQ-COPILOT-002`, `REQ-COPILOT-004`).
- [x] `TASK-4.3` [RED/GREEN]: Implement canonical `ParametersHashUtil` and unit tests in `ParametersHashUtilTest`:
  - Canonical JSON normalization (sorted keys, normalized numbers, omitted nulls) before SHA-256 computation (`REQ-COPILOT-003`, `I-AI-007`).

### Phase 2: PostgreSQL Persistence & Schema Migration ([MUST])
- [x] `TASK-4.4` [GREEN]: Add `copilot_proposals` table to `docker/init/schema.sql` with all ARCH-mandated columns and constraints:
  - Columns: `id`, `tenant_id`, `wallet_id`, `type`, `parameters_json`, `parameters_hash`, `status`, `idempotency_key`, `execution_operation_id`, `created_by`, `approved_by`, `created_at`, `expires_at`, `execution_claimed_at`, `execution_lease_until`, `approved_at`, `executed_at`, `execution_reference`.
  - Constraints: `chk_copilot_proposal_status`, `uq_copilot_proposal_tenant_idempotency UNIQUE(tenant_id, idempotency_key)`, `uq_copilot_proposal_execution_op_id UNIQUE(execution_operation_id)`.
  - Indexes: `idx_copilot_proposals_tenant_wallet_status`, `idx_copilot_proposals_expires_at`, `idx_copilot_proposals_stale_lease` (`REQ-COPILOT-002`, `I-AI-006`).
- [x] `TASK-4.5` [RED/GREEN]: Implement `ProposalDao` with Spring JDBC and integration tests in `ProposalDaoIT` with Testcontainers PostgreSQL:
  - `insert(proposal)` handling `ON CONFLICT (tenant_id, idempotency_key) DO NOTHING` and querying existing record.
  - `claimForExecution(id, tenantId, principal, now, leaseUntil)`: atomic update `WHERE id = :id AND tenant_id = :tenantId AND status = 'PROPOSED' AND expires_at >= :now`.
  - Conditional transition updates: `markExecuted`, `markRejected` (`WHERE status = 'PROPOSED'`), `markInvalidated`, `markExpired`.
  - Queries: `findById(id, tenantId)`, `findPendingByWalletId`, `findStaleExecutingLeases(now)`.
  - Test assertions on immutability of `execution_operation_id`, JSONB parameters, and SHA-256 integrity (`REQ-COPILOT-002`, `REQ-COPILOT-005`, `I-AI-005`, `I-AI-007`, `I-AI-008`).

### Phase 3: Downstream Execution Bridge & Lease Reconciliation ([MUST])
- [x] `TASK-4.6` [RED/GREEN]: Implement `DownstreamExecutionBridge` and unit tests in `DownstreamExecutionBridgeTest`:
  - Dispatches to `TransferFundsUseCase` (`TRANSFER`), `SavingsPlanUseCase` (`SAVINGS_RULE`), `FinancialGoalUseCase` (`GOAL_ADJUSTMENT`) with stable `executionOperationId`.
  - Integrates `OperationQueryUseCase.getOperationStatus(executionOperationId)` to query downstream status during recovery.
  - Segregates failure modes: business exceptions (`InsufficientFundsException`, `AccountBlockedException`) classified as `BUSINESS_FAILURE` $\to$ proposal transitions to `INVALIDATED`; technical exceptions (`TransientException`, timeouts) classified as `TECHNICAL_FAILURE` $\to$ proposal remains in `EXECUTING` under lease without invalidation (`I-AI-009`, `I-AI-010`, `REQ-COPILOT-009`, `REQ-COPILOT-010`).
- [x] `TASK-4.7` [RED/GREEN]: Implement `ProposalService.createProposal` with creation idempotency and tests in `ProposalServiceTest`:
  - Duplicate `(tenantId, idempotencyKey)` with identical canonical `parameters_hash` returns existing proposal (`200 OK`).
  - Replay of key with conflicting `parameters_hash` throws `IdempotencyConflictException` (HTTP 409).
  - Assigns immutable UUID `executionOperationId` prior to any execution claim (`REQ-COPILOT-003`, `I-AI-006`, `I-AI-008`).
- [x] `TASK-4.8` [RED/GREEN]: Implement `ProposalService.approveProposal` & `rejectProposal` with tests in `ProposalServiceTest`:
  - State machine response policy:
    - `PROPOSED` within TTL: claims atomic lease $\to$ dispatches downstream $\to$ returns 200 OK with `status: EXECUTED`.
    - `EXECUTING` with valid lease: returns HTTP 409 Conflict (processing in progress).
    - `EXECUTING` with expired lease: routes to safe reconciliation (`TASK-4.9`).
    - `EXECUTED`: returns cached `ProposalResponse` (HTTP 200 OK) without re-executing (`REQ-COPILOT-008`, `I-AI-006`).
    - Terminal (`REJECTED`, `EXPIRED`, `INVALIDATED`): returns HTTP 409 Conflict.
  - Rejection: conditionally transitions `PROPOSED` $\to$ `REJECTED`; rejection fails if already `EXECUTING` or `EXECUTED`.
  - Synchronous TTL gate: transitions to `EXPIRED` and throws `ProposalExpiredException` (HTTP 409) if `now > expiresAt` (`REQ-COPILOT-006`, `I-AI-005`).
- [x] `TASK-4.9` [RED/GREEN]: Implement `ProposalLeaseReconciler` and tests in `ProposalLeaseReconcilerTest`:
  - Scans stale leases (`status = 'EXECUTING'` and `now > execution_lease_until`).
  - Tri-state downstream outcome:
    1. Downstream completed (found in `OperationQueryUseCase`): updates proposal to `EXECUTED` with cached reference, zero duplicate mutations.
    2. Downstream provably not executed: re-dispatches execution using the SAME `executionOperationId` reusing original human authorization (`I-AI-001`).
    3. Downstream unknown / timeout: maintains `EXECUTING` with lease extension; never invalidates on transient failure (`I-AI-010`).

### Phase 4: Authenticated REST Ingress & Security Context ([MUST])
- [x] `TASK-4.10` [RED/GREEN]: Implement MockMvc integration tests in `ProposalControllerTest`:
  - Extracts principal and tenant strictly from authenticated `SecurityContext` (`REQ-COPILOT-011`).
  - Asserts cross-tenant requests return HTTP 404 Not Found to prevent resource existence enumeration (`I-AI-004`).
  - Tests endpoints: `POST /api/v1/copilot/proposals`, `POST /api/v1/copilot/proposals/{id}/approve`, `POST /api/v1/copilot/proposals/{id}/reject`, `GET /api/v1/copilot/proposals/{id}`, `GET /api/v1/copilot/proposals?walletId={w}&status={s}` (`REQ-COPILOT-007`).
- [x] `TASK-4.11` [GREEN]: Implement `ProposalController` in `br.com.wallet.copilot.internal.rest` (`REQ-COPILOT-007`, `REQ-COPILOT-011`).

### Phase 5: Concurrency & Crash Recovery Integration Verification ([MUST])
- [x] `TASK-4.12` [RED/GREEN]: Implement comprehensive Testcontainers integration tests in `ProposalConcurrentApprovalIT` and `ProposalLeaseRecoveryIT`:
  - Concurrency Gate: 100 concurrent threads attempting to approve same proposal $\to$ exactly 1 succeeds, downstream executes exactly once (`I-AI-005`).
  - Competing Transitions Gate: approval vs rejection race $\to$ only one transition succeeds, other receives 409 Conflict.
  - Crash Recovery Gate A (crash before downstream): lease expires $\to$ reconciler reuses human authorization and `executionOperationId`, completing downstream mutation once (`I-AI-001`, `I-AI-010`).
  - Crash Recovery Gate B (crash after downstream): downstream executed, node crashed before updating proposal status $\to$ reconciler detects completion via `OperationQueryUseCase` and marks `EXECUTED` without duplicate ledger write (`I-AI-006`, `I-AI-010`).
  - Downstream Idempotency Gate: proves identical `executionOperationId` cannot produce duplicate ledger entries.

### Phase 6: Housekeeping, Events & Optional Metadata ([SHOULD] / [COULD])
- [x] `TASK-4.13` [RED/GREEN]: Implement housekeeping sweeper, domain events, and metadata (`REQ-COPILOT-012`, `REQ-COPILOT-013`, `REQ-COPILOT-014`):
  - `ProposalTtlSweeper`: `@Scheduled` background worker finding overdue `PROPOSED` proposals and marking them `EXPIRED` (`REQ-COPILOT-012`).
  - Modulith domain events (`ProposalCreatedEvent`, `ProposalApprovedEvent`, `ProposalExecutedEvent`) registered with Event Publication Registry (`event_publication`) (`REQ-COPILOT-013`).
  - Storage of optional agent rationale narrative and risk evaluation metadata (`REQ-COPILOT-014`).

### Phase 7: Architectural Convergence Gate (`I-SDD-002`, `I-SDD-003`)
- [x] `TASK-4.14` [GREEN]: **Final Convergence Gate**:
  - Run `./gradlew test` asserting all unit and Testcontainers integration tests pass with real execution evidence.
  - Run `ModulithArchitectureTest.verifyArchitecture()` asserting 0 violations (`REQ-COPILOT-001`).
  - Bi-directional equivalence reconciliation asserting 100% congruence between code, DDL, and specs (`I-SDD-003`).
  - Author `SUMMARY-004-ai-financial-copilot-and-proposal-domain.md` with Practical Verification Guide and seed fixtures (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

- [x] All unit tests pass: `./gradlew test`
- [x] All integration tests pass: Testcontainers suite green with actual execution evidence
- [x] Modulith architecture verification passes (`ModulithArchitectureTest.verifyArchitecture()`) with 0 violations
- [x] Zero compiler / linter warnings
- [x] OpenTelemetry traces & `executionOperationId` propagation verified
- [x] Downstream contract verified: zero duplicate ledger entries under crash and recovery
- [x] **Zero Spec-Drift Reconciliation (`I-SDD-003`)**: Class names, package paths, and DDL schemas match `src/` 100%
- [x] All task checkboxes in `TASKS-004.md` marked `[x]`
- [x] Author Practical Verification Guide & Seed Data in `SUMMARY-004.md` (`I-SDD-002`)
