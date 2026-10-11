# 📋 Specification: SPEC-004.1 — MCP Gateway & Human Interaction

- **Status**: 📝 Approved / Ratified (Refined per History 104)
- **Author**: Antigravity Orchestrator & System Architect
- **Date**: 2026-10-09
- **Target Release / Milestone**: Wallet Service V4 — Phase 4.1
- **Bounded Context / Package**: `br.com.wallet.copilot.internal.mcp` (Edge Adapter)
- **Spec Slicing Scope**: Max 250 lines (`I-SDD-006`). Domain engine defined in `SPEC-004`.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - `.histories/history95.txt`–`97.txt`: MCP is strictly transport adapter; capability $\ne$ module $\ne$ tool; Spring AI WebFlux/WebMvc Streamable HTTP.
  - `.histories/history102.txt`–`104.txt`: Streamable HTTP with session vs stateless; Form elicitation response $\ne$ authorization credential; URL elicitation anti-bearer contract (opaque correlation token, independent human auth); multi-instance safe tracker (fail-safe to Web/CLI, zero TTL extension, zero duplicate execution); tool idempotency scoped to `(tenantId, idempotencyKey)` with canonical payload normalization; Spring AI ownership of JSON-RPC framing.
- **Architectural Mantra**: *"The agent requests. The gateway interacts. The human authorizes via a trusted, independent mechanism. The financial domain decides and executes."*
- **Foundational Constraints (`constitution.md`)**:
  - `I-AI-001` (Human-in-the-Loop Gate): AI agents never execute mutations directly.
  - `I-AI-004` (Authenticated Tenant & 404 Non-Disclosure): Zero client spoofing; cross-tenant resource hiding.
  - `I-TDD-002` (Zero Vibe Coding): Exact `BigDecimal` scale 2 arithmetic; mandatory test triads.

---

## 1. Intent & Business Value

Expose Wallet capabilities (`ledger`, `intelligence`, `goals`, `savings`) to external AI agents via the Model Context Protocol (MCP). The MCP Gateway serves as an authenticated protocol adapter using Spring AI with Streamable HTTP (`STREAMABLE` session-oriented transport). It delivers read-only inquiry tools and mutation proposal tools backed by MCP Elicitation and Multi-Round-Trip Requests (MRTR), delegating financial authorization and lifecycle strictly to `SPEC-004` (`copilot.api.ProposalUseCase`).

---

## 2. Scope & Non-Goals

### In Scope
- **Authenticated Spring AI MCP Server**: Streamable HTTP with session; server-enforced transport security before tool execution (`I-MCP-005`).
- **Read-Only Tool Catalog**: Pure queries asserting resource tenant ownership before executing downstream use cases.
- **Mutation Proposal Tools**: Adapters delegating to `copilot.api.ProposalUseCase.createProposal` with mandatory idempotency key and canonical scale-2 bounds.
- **Three-Tier Capability Negotiation**: Separate inspection for Form and URL Elicitation with deterministic external Web/CLI fallback (`REQ-MCP-005`).
- **Human Verification Gate**: Treating elicitation `accept` as an interaction event, requiring independent human credentials before calling `approveProposal()`.
- **Anti-Bearer URL Elicitation**: Opaque single-use tokens over HTTPS requiring independent web authentication and user matching.
- **Multi-Instance Safe Interaction Tracking**: Ephemeral tracker failing safe to Web/CLI without altering proposal TTL or duplicating execution.

### Non-Goals
- Financial proposal persistence, leases, state machine, or downstream execution (owned by `SPEC-004`).
- Treating agent responses or unverified elicitation payloads as financial authorization (`I-MCP-002`, `I-MCP-007`).
- Requesting or transmitting sensitive credentials (passwords, PINs, PANs) via MCP forms (`I-MCP-003`).
- Custom reimplementation of JSON-RPC 2.0 envelopes already provided by Spring AI.

---

## 3. Cross-Feature & Invariant Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Side Effect / Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`copilot.domain`** (`SPEC-004`) | Proposal creation and approval dispatch | Agent attempts to bypass human gate | `I-AI-001`, `I-MCP-002`: Elicitation never bypasses `ProposalUseCase.approveProposal` |
| **`security`** | Endpoint authentication | Header spoofing / unauthenticated access | `I-MCP-005`: Server-validated SecurityContext; tool params cannot override tenant |
| **`ledger.api`** | Read queries (`BalanceUseCase`) | Connection exhaustion via open streams | Stateless MRTR; non-locking balance projections (`I-CASH-006`) |
| **`intelligence.api`** | Cashflow & subscription queries | Conversational polling loops | Ingress rate limiting; tenant ownership check prior to query |
| **`goals.api`** | Strategy simulation tool | Accidental state mutation during simulation | Tool contract is strictly pure simulation; zero write methods called |

---

## 4. Mathematical & System Invariants

- **`I-MCP-001` (Transport Adapter Isolation)**: `br.com.wallet.copilot.api` MUST NOT import or reference Spring AI or MCP types.
- **`I-MCP-002` (Elicitation Non-Bypass & Decoupled Authorization Gate)**:
  Client elicitation responses (`accept`, `confirmed: true`) are interaction events, NEVER financial authorization credentials. Approval MUST be dispatched through `SPEC-004` with independent server-validated human principal. If unverified, proposal remains `PROPOSED` with external Web/CLI instructions.
- **`I-MCP-003` (Zero Sensitive Credentials & Anti-Bearer URL Security)**:
  Passwords, PINs, and PANs MUST NOT be requested via MCP forms. URL Elicitation MUST use HTTPS, opaque single-use tokens, zero plain parameters, and require independent human authentication and subject matching. Consuming or viewing a URL MUST NOT constitute authorization.
- **`I-MCP-004` (Interaction vs Financial Lifecycle Decoupling & Multi-Instance Safety)**:
  MCP interaction state is ephemeral and decoupled from `FinancialProposal` state. Lost correlation, pod restarts, or multi-instance routing fail safe to external Web/CLI. Interaction events NEVER recreate proposals, renew TTL, or trigger duplicate downstream execution.
- **`I-MCP-005` (Strict Server-Enforced Authentication & Zero Agent Spoofing)**:
  `tenantId` and `userId` MUST derive exclusively from server-validated `SecurityContext`. Tool arguments MUST NOT override authenticated tenant/user. Unauthenticated requests MUST fail immediately (HTTP 401/403).
- **`I-MCP-006` (Monetary Precision & Mandatory Idempotency)**:
  Tool transfer amounts MUST be canonical scale-2 strings (`^[0-9]+\.[0-9]{2}$`), bounded in $[0.01, 100000000.00]$, converted directly to `BigDecimal`. `idempotencyKey` scoped to `(tenantId, idempotencyKey)` is mandatory; identical payload returns existing proposal, divergent payload returns conflict.
- **`I-MCP-007` (Three-Identity Non-Conflation Gate)**:
  Transport Principal (HTTP token) $\ne$ Agent Client (LLM runtime) $\ne$ Human Authorizer (account owner). An agent client MUST NOT authorize financial proposals on behalf of a human. Approval strictly requires authenticated human principal verification via `SPEC-004`.

---

## 5. Functional Requirements (MoSCoW — `I-SDD-004`)

### 5.1 Must Have (`[MUST]`)
- **`REQ-MCP-001 [MUST]`**: Configure Spring AI MCP Server utilizing Streamable HTTP (`STREAMABLE` protocol mode) with server-enforced transport authentication (`I-MCP-005`).
- **`REQ-MCP-002 [MUST]`**: Implement Read-Only MCP Tool Catalog asserting resource tenant ownership before query:
  - `wallet_get_balance(walletId)` $\to$ `ledger.api.BalanceUseCase`
  - `wallet_get_cashflow_projections(walletId)` $\to$ `intelligence.api.CashflowQueryUseCase`
  - `wallet_get_active_subscriptions(walletId)` $\to$ `intelligence.api.SubscriptionQueryUseCase`
  - `wallet_simulate_goal_strategy(goalId, targetAmount, targetDate)` $\to$ pure stateless simulation via `goals.api.GoalStrategyUseCase`
- **`REQ-MCP-003 [MUST]`**: Implement Mutation Proposal Tools delegating to `copilot.api.ProposalUseCase.createProposal` with mandatory `idempotencyKey` and canonical scale-2 amount:
  - `copilot_propose_transfer(sourceWalletId, targetWalletId, amount, description, idempotencyKey)`
  - `copilot_propose_savings_rule(planId, ruleType, percentage, threshold, step, idempotencyKey)`
  - `copilot_propose_goal_adjustment(goalId, name, targetAmount, targetDate, priority, idempotencyKey)`
- **`REQ-MCP-004 [MUST]`**: Propagate Authenticated Security Context (`tenantId`, `userId`) to all tool executions; reject tool argument tenant overrides.
- **`REQ-MCP-005 [MUST]`**: Implement Dual-Capability Negotiation: separately inspect client capabilities for `form_elicitation` and `url_elicitation`. If neither is supported, return proposal details and instructions for external human approval (`I-MCP-002`).

### 5.2 Should Have (`[SHOULD]`)
- **`REQ-MCP-006 [SHOULD]`**: Implement interactive Form Elicitation for compatible clients, verifying independent human credentials before dispatching to `ProposalUseCase.approveProposal` (`I-MCP-002`, `I-MCP-007`).
- **`REQ-MCP-007 [SHOULD]`**: Support MRTR Request Resumption with ephemeral multi-instance safe tracking, failing safe to Web/CLI without altering proposal TTL (`I-MCP-004`).
- **`REQ-MCP-008 [SHOULD]`**: Implement Secure URL Elicitation with short-lived single-use opaque tokens over HTTPS, requiring independent authentication and subject matching (`I-MCP-003`).

### 5.3 Could Have (`[COULD]`)
- **`REQ-MCP-009 [COULD]`**: Format financial projection and subscription summaries with markdown tables for conversational clients.

### 5.4 Won't Have This Time (`[WON'T]`)
- Autonomous mutation execution bypassing `SPEC-004` human authorization (`I-AI-001`).
- Direct database DAO access or bypass of Spring Modulith `.api` packages (`I-MODULITH-001`).
- Custom reinvention of JSON-RPC envelopes handled natively by Spring AI.

---

## 6. Interface Contracts & Interaction Flows

### 6.1 Mutation Proposal Tool Schema
```json
{
  "name": "copilot_propose_transfer",
  "description": "Propose an asynchronous financial transfer requiring explicit human confirmation",
  "inputSchema": {
    "type": "object",
    "properties": {
      "sourceWalletId": { "type": "string", "format": "uuid" },
      "targetWalletId": { "type": "string", "format": "uuid" },
      "amount": { "type": "string", "pattern": "^[0-9]+\\.[0-9]{2}$", "description": "Canonical scale-2 monetary amount" },
      "description": { "type": "string" },
      "idempotencyKey": { "type": "string", "description": "Mandatory client idempotency key" }
    },
    "required": ["sourceWalletId", "targetWalletId", "amount", "idempotencyKey"]
  }
}
```

### 6.2 Elicitation-to-Authorization Protocol Flow
```text
[Agent] ──(1. callTool)──> [MCP Gateway] ──(2. createProposal)──> [copilot.api] (Status: PROPOSED)
                                │
          ┌─────────────────────┴──────────────────────┐
          ▼ (Client supports Form)                     ▼ (No Elicitation / Fallback)
   [3a. Form Elicitation]                       [3b. Return Proposal Response]
          │                                            │ (external instructions)
   [4a. Interaction Response: accept]                  ▼
          │                                      [Human Approves via Web/CLI]
   [5a. Gateway verifies HUMAN principal]              │
        (fails? -> directs to Web/CLI)                 ▼
        (verified? -> approveProposal())        [SPEC-004 Domain executes]
          │                                            │
          ▼                                            │
   [6a. Status: EXECUTED] <────────────────────────────┘
```

---

## 7. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Boundary / Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-MCP-001` (Transport & Auth) | Authenticated handshake & tool execution | Unauthenticated request $\to$ 401/403 | Missing security context allows execution (`I-MCP-005`) |
| `REQ-MCP-002` (Read Tools) | Returns balance for verified owner wallet | Invalid UUID or cross-tenant wallet $\to$ 404 | Tool bypasses `ledger.api` to read DB (`I-MCP-001`) |
| `REQ-MCP-003` (Proposals) | Creates proposal via `copilot.api` | Scale $> 2$, missing key, negative amount $\to$ error | Tool executes transfer directly (`I-AI-001`) |
| `REQ-MCP-004` (Context) | Verified principal propagated to domain | Caller provides mismatched `tenantId` in params | Tool param overrides security context (`I-MCP-005`) |
| `REQ-MCP-005` (Negotiation) | Client without elicitation gets external URL | Partial capability declaration (Form only) | Unsupported client triggers auto-execution (`I-MCP-002`) |
| `REQ-MCP-006` (Form Elicitation)| Verified human confirm triggers approval | Agent accept without human credentials rejected | Agent response treated as human approval (`I-MCP-002`, `I-MCP-007`) |
| `REQ-MCP-007` (MRTR Resumption) | Reconnected client resumes pending interaction| Pod restart / tracker loss falls back to Web/CLI | Resumption duplicates proposal or extends TTL (`I-MCP-004`) |
| `REQ-MCP-008` (URL Elicitation) | Single-use opaque HTTPS token rendered | Token opened by wrong user or expired $\to$ 403 | Plain credentials or token acts as bearer auth (`I-MCP-003`) |

---

## 8. Acceptance Criteria & Practical Verification (`I-SDD-002`)

- [ ] Spring AI MCP Server configured on Streamable HTTP with endpoint security filter.
- [ ] Tool catalog verified: all 4 read tools and 3 proposal tools operate exclusively via module `.api` contracts.
- [ ] Modulith architecture verification passes with zero dependency leaks from `copilot.api` to MCP.
- [ ] Handshake capability test proves deterministic fallback to external URI when client lacks elicitation.
- [ ] Security test proves agent cannot approve proposal without verified human credentials (`testAgentCannotApprove`).
- [ ] Concurrency & resumption tests prove multi-instance state loss does not compromise proposal integrity.
