# 📋 Specification: SPEC-004 — AI Financial Copilot & Proposal Domain

- **Status**: Draft (Refined per History 98 Review)
- **Author**: Antigravity Orchestrator & System Architect
- **Date**: 2026-10-08
- **Target Release / Milestone**: Wallet Service V4 — Phase 4.0
- **Bounded Context / Module**: `br.com.wallet.copilot`
- **Spec Slicing Scope**: Max 250 lines (`I-SDD-006`). Adapter/MCP layer sliced into `SPEC-004.1`.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - `.histories/history95.txt` & `history97.txt`: Slicing into SPEC-004 (Domain) and SPEC-004.1 (MCP Gateway).
  - `.histories/history98.txt`: Atomic execution claim, creation idempotency, HTTP 404 non-disclosure, technical failure vs business invalidation, separation of execution identities (`proposalId`, `executionOperationId`, `approvalRequestId`).
- **Foundational Invariants (`constitution.md`)**:
  - `I-AI-001` (Human-in-the-Loop for AI Writes): AI agents MUST NOT unilaterally execute mutations.
  - `I-AI-002` (Critical Path Isolation): Core transactions must never depend on AI/MCP availability.
  - `I-MODULITH-001` / `002`: Internal encapsulation; interactions strictly via `.api` packages.
  - `I-CONCURRENCY-001` / `I-ATOMICITY-001`: Downstream use cases enforce row-level locking.

---

## 1. Intent & Business Value

Establish `br.com.wallet.copilot` as a first-class financial capability providing an asynchronous, auditable **Financial Proposal Engine**. External agents or client applications propose financial actions (`PROPOSED`). Mutations execute strictly upon explicit human authorization (`POST /proposals/{id}/approve`), with immutable parameter snapshots, atomic execution claims, stable `executionOperationId`, authoritative TTL gates, and idempotent downstream use-case dispatch.

---

## 2. Scope & Non-Goals

### In Scope
- **Spring Modulith Module (`br.com.wallet.copilot`)**: Domain models, lifecycle, API contracts, Modulith architecture verification.
- **FinancialProposal Lifecycle Engine**: States (`PROPOSED`, `EXECUTED`, `REJECTED`, `EXPIRED`, `INVALIDATED`).
- **Proposal Creation Idempotency**: `(tenantId, idempotencyKey)` prevents duplicate proposals on client retry.
- **Atomic Execution Claim**: Single-winner conditional transition preventing concurrent duplicate execution dispatches.
- **Immutable Proposal Snapshot**: Parameters frozen at creation time; sole authority for downstream execution.
- **Authoritative TTL Enforcement**: Synchronous expiration check ($\Delta t = 15\text{ min}$) at approval time.
- **Human Authorization Ingress**: REST API (`/approve`, `/reject`, `/proposals`) with authenticated tenant security context.
- **Identity Triad**: `proposalId` (entity), `executionOperationId` (financial intent), `approvalRequestId` (attempt).
- **Downstream Bridge**: Delegation to `ledger.api`, `savings.api`, and `goals.api` without reimplementing financial rules.

### Non-Goals
- MCP protocol, Spring AI, Streamable HTTP, or JSON-RPC schemas (owned by `SPEC-004.1`).
- Autonomous ledger mutations bypassing human authorization (`I-AI-001`).
- Reimplementing authoritative balance calculations or double-entry accounting inside Copilot.

---

## 3. Cross-Feature & Invariant Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Side Effect / Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`ledger`** (Core) | Transfer execution via `TransferFundsUseCase` | Double-execution from retried proposal approval | `I-AI-006`: Stable `executionOperationId`; ledger idempotency table |
| **`savings`** (Automation) | Plan/Rule provisioning via `SavingsPlanUseCase` | Conflicting or duplicate savings rule creation | Domain precondition check; idempotent rule matching |
| **`goals`** (Strategy) | Target/Timeline adjustment via `GoalUseCase` | Infeasible goal adjustment applied without checks | Goal strategy validation during approval execution |
| **`security`** (Perimeter) | Tenant context & resource disclosure | Tenant spoofing / Resource existence oracle | `I-AI-004`: SecurityContext principal only; 404 on cross-tenant |

---

## 4. Mathematical & System Invariants

- **`I-AI-001` (Human Authorization Gate & Execution Invariant)**:
  A financial execution MUST originate from an explicit human approval of the proposal. Once execution ownership is atomically claimed, subsequent retries or lease recoveries MUST reuse the existing human authorization and immutable `executionOperationId`, and MUST NOT require a new approval:
  $$\text{ClaimExecution}(p) \iff \text{HumanApprove}(p.\text{id}) \land \text{Status} = \text{PROPOSED} \land \text{now}() \le p.\text{expiresAt}$$
  $$\text{Execute}(p) \iff (\text{ClaimExecution}(p) \lor \text{RecoverClaim}(p)) \land \text{Status} = \text{EXECUTING}$$
- **`I-AI-004` (Authenticated Tenant Context & Non-Disclosure Gate)**:
  `TenantContext` MUST be derived strictly from the authenticated security principal. Cross-tenant access attempts MUST return HTTP 404 Not Found to prevent resource existence enumeration.
- **`I-AI-005` (Atomic Execution Claim & Authoritative TTL Gate)**:
  Only one approval request may claim execution ownership. The transition MUST be atomic/conditional:
  $$\text{Claim}(p) \iff \text{Status} = \text{PROPOSED} \land \text{now}() \le p.\text{expiresAt}$$
  If $\text{now}() > p.\text{expiresAt}$, approval is rejected with HTTP 409 / `ProposalExpiredException` and transitions to `EXPIRED`.
- **`I-AI-006` (Immutable Execution Identity & Idempotency)**:
  A `FinancialProposal` MUST carry an immutable `executionOperationId` assigned at creation. Retried approvals MUST reuse the exact same `executionOperationId`. Downstream use cases enforce idempotency on this ID.
- **`I-AI-007` (Parameters Snapshot Immutability)**:
  The `parameters` payload recorded at creation is an immutable snapshot frozen with SHA-256 `parameters_hash`. Execution MUST apply the exact parameters stored in the proposal without external re-reading or tampering.
- **`I-AI-008` (Proposal Creation Idempotency & Conflict Gate)**:
  Submitting the same `(tenantId, idempotencyKey)` with an identical semantic payload MUST return the existing proposal. Reusing the key with a materially different payload MUST be rejected with HTTP 409 / `IdempotencyConflictException`.
- **`I-AI-009` (Precondition Responsibility Boundary)**:
  Copilot MAY orchestrate coarse preconditions, but authoritative financial rules (balance checks, row locking) are owned and enforced exclusively by downstream use cases (`ledger`, `savings`, `goals`).
- **`I-AI-010` (Business Invalidation vs Technical Failure with Lease Recovery)**:
  Business precondition breaches (insufficient balance, inactive account) transition proposal to `INVALIDATED`. Technical infrastructure failures (network timeouts, downstream outages) leave the proposal in `EXECUTING` protected by `execution_lease_until` and MUST NOT transition to `INVALIDATED`; recovery reconciles or retries the same `executionOperationId`.

---

## 5. Functional Requirements (MoSCoW — `I-SDD-004`)

### 5.1 Must Have (`[MUST]`)
- **`REQ-COPILOT-001 [MUST]`**: Define Modulith module `br.com.wallet.copilot` with public `api` and sealed `internal`.
- **`REQ-COPILOT-002 [MUST]`**: Define `FinancialProposal` entity: `proposalId`, `tenantId`, `walletId`, `type`, `parametersJson`, `status`, `idempotencyKey`, `createdAt`, `expiresAt`, `executionOperationId`, `createdBy`, `approvedBy`, `approvedAt`, `executedAt`, `executionReference`.
- **`REQ-COPILOT-003 [MUST]`**: Implement Proposal Creation Idempotency: `(tenantId, idempotencyKey)` returns existing proposal or 409 on payload mismatch.
- **`REQ-COPILOT-004 [MUST]`**: Implement Proposal Lifecycle state machine: `PROPOSED` $\to$ `EXECUTING` (internal claim) $\to$ `EXECUTED`, or `REJECTED`, `EXPIRED`, `INVALIDATED`.
- **`REQ-COPILOT-005 [MUST]`**: Implement Atomic Execution Claim with stale lease recovery preventing concurrent approval races.
- **`REQ-COPILOT-006 [MUST]`**: Implement Synchronous Authoritative TTL Gate rejecting approvals when $\text{now}() > \text{expiresAt}$.
- **`REQ-COPILOT-007 [MUST]`**: Implement Human Authorization REST API:
  - `POST /api/v1/copilot/proposals/{id}/approve` $\longrightarrow$ validates TTL, claims execution, dispatches downstream synchronously.
  - `POST /api/v1/copilot/proposals/{id}/reject` $\longrightarrow$ transitions status to `REJECTED`.
  - `GET /api/v1/copilot/proposals?walletId={w}&status=PROPOSED` $\longrightarrow$ lists proposals for authenticated tenant.
  - Cross-tenant requests MUST return HTTP 404 (`I-AI-004`).
- **`REQ-COPILOT-008 [MUST]`**: Implement Execution Idempotency: approval of already `EXECUTED` proposal returns cached execution reference without re-executing downstream.
- **`REQ-COPILOT-009 [MUST]`**: Implement Precondition Validation: distinguish business invalidation (`INVALIDATED`) from technical failure (`I-AI-010`).
- **`REQ-COPILOT-010 [MUST]`**: Implement Downstream Bridge dispatching approved proposals to `ledger.api`, `savings.api`, `goals.api` via `executionOperationId`.
- **`REQ-COPILOT-011 [MUST]`**: Enforce Human Principal Authorization: `createdBy` and `approvedBy` MUST be resolved from `SecurityContext`.

### 5.2 Should Have (`[SHOULD]`)
- **`REQ-COPILOT-012 [SHOULD]`**: Implement asynchronous background sweeper job marking overdue proposals as `EXPIRED` (housekeeping, not TTL authority).
- **`REQ-COPILOT-013 [SHOULD]`**: Emit Spring Modulith domain events `ProposalCreatedEvent`, `ProposalApprovedEvent`, and `ProposalExecutedEvent`.

### 5.3 Could Have (`[COULD]`)
- **`REQ-COPILOT-014 [COULD]`**: Store agent rationale narrative and risk evaluation metadata in proposal parameters snapshot.

### 5.4 Won't Have This Time (`[WON'T]`)
- Direct execution of mutations bypassing human authorization (`I-AI-001`).
- MCP JSON-RPC protocol handling (delegated to `SPEC-004.1`).

---

## 6. Interface Contracts

### 6.1 Human Approval REST Ingress
```http
POST /api/v1/copilot/proposals/e1000000-0000-0000-0000-000000000001/approve
Authorization: Bearer <authenticated-token>
Response (200 OK):
{
  "proposalId": "e1000000-0000-0000-0000-000000000001",
  "status": "EXECUTED",
  "executionOperationId": "c1000000-0000-0000-0000-000000000001",
  "executionReference": "tx-883910-abcd",
  "executedAt": "2026-10-08T14:00:00Z"
}
```

### 6.2 Proposal Creation Contract (`copilot::api`)
```java
public record CreateProposalCommand(
    UUID walletId,
    ProposalType type,
    String parametersJson,
    String idempotencyKey
) {}
```

---

## 7. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Boundary / Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-COPILOT-003` (Creation) | Proposal created with unique idempotencyKey | Blank key or parameters $\to$ 400 Bad Request | Replayed `idempotencyKey` creates 2nd proposal $\to$ Breach (`I-AI-008`) |
| `REQ-COPILOT-005` (Atomic Claim)| 100 concurrent approvals $\to$ exactly 1 claims execution and dispatches downstream | Approval after expiration $\to$ HTTP 409 / `EXPIRED` | Concurrent approvals dispatch multiple transfers $\to$ Breach (`I-AI-005`) |
| `REQ-COPILOT-008` (Idempotency) | Retrying `EXECUTED` proposal returns cached execution reference | Invalid transition (`REJECTED` $\to$ approve) $\to$ 409 | Retry triggers 2nd downstream mutation $\to$ Breach (`I-AI-006`) |
| `REQ-COPILOT-009` (Precondition)| Business failure (insufficient funds) $\to$ `INVALIDATED` | Technical outage (DB timeout) $\to$ Retried, NOT invalidated | Technical failure permanently invalidates proposal $\to$ Breach (`I-AI-010`) |
| `REQ-COPILOT-007` (Security) | Tenant matches proposal $\to$ Access permitted | Missing authentication $\to$ 401 Unauthorized | Tenant A accesses Tenant B proposal $\to$ 404 Not Found (`I-AI-004`) |

---

## 8. Acceptance Criteria & Practical Verification (`I-SDD-002`)

### 8.1 Acceptance Criteria
- [ ] All `[MUST]` requirements implemented with unit & integration tests (`./gradlew test`).
- [ ] `ModulithArchitectureTest.verifyArchitecture()` passes with 0 violations.
- [ ] Zero Spec-Drift: Code, schemas, and specs are 100% congruent (`I-SDD-003`).

### 8.2 Practical Verification Invariants
1. **Creation Idempotency**: Re-submitting identical `CreateProposalCommand` produces identical `proposalId`.
2. **Atomic Approval Claim**: Concurrent approval attempts converge on single downstream mutation.
3. **Tenant Non-Disclosure**: Cross-tenant requests return 404 without leaking proposal existence.
