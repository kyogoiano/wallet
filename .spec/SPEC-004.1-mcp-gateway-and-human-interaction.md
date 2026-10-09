# 📋 Specification: SPEC-004.1 — MCP Gateway & Human Interaction

- **Status**: Draft (Created per History 96 & 97)
- **Author**: Antigravity Orchestrator & System Architect
- **Date**: 2026-10-08
- **Target Release / Milestone**: Wallet Service V4 — Phase 4.1
- **Bounded Context / Package**: `br.com.wallet.copilot.internal.mcp`
- **Spec Slicing Scope**: Max 250 lines (`I-SDD-006`). Domain engine defined in `SPEC-004`.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - `.histories/history95.txt`: MCP is strictly transport/adapter; capability ≠ module ≠ tool.
  - `.histories/history96.txt`: Streamable HTTP replaces legacy SSE; Spring AI MCP WebFlux; MCP Form & URL Elicitation; MRTR (`input_required`); interaction state $\ne$ proposal state.
  - `.histories/history97.txt`: Slicing into SPEC-004.1; Spring Boot 4.2.0-M2 compatibility; elicitation capability negotiation.
- **Foundational Constraints (`constitution.md`)**:
  - `I-AI-001` (Human-in-the-Loop): AI agents never execute mutations directly.
  - `I-AI-002` (Critical Path Isolation): Ingress performance/availability isolated from core banking.
  - `I-AI-003` (Modulith Boundary): Tool adapters consume public `.api` contracts only.

---

## 1. Intent & Business Value

Expose Wallet capabilities (`ledger`, `intelligence`, `goals`, `savings`) to external AI agents through the standardized Model Context Protocol (MCP). The MCP Gateway acts as a protocol adapter on Spring AI with Streamable HTTP and reactive WebFlux, providing read-only inquiry tools and mutation proposal tools backed by MCP Elicitation and Multi-Round-Trip Requests (MRTR), delegating all financial authorization to `SPEC-004` (`copilot::api`).

---

## 2. Scope & Non-Goals

### In Scope
- **Spring AI MCP Server**: WebFlux with Streamable HTTP transport conforming to Spring Boot 4.2.0-M2 baseline.
- **Read-Only Tool Catalog**: Pure queries (`balance`, `cashflow_projections`, `subscriptions`, `simulate_goal`).
- **Mutation Proposal Tools**: Thin adapters delegating to `copilot::api` (`CreateProposalCommand`).
- **MCP Elicitation Support**: Form Elicitation for interactive approval; URL Elicitation for out-of-band sensitive flows.
- **Capability Negotiation**: Graceful degradation to external approval when client lacks elicitation support.
- **Context & Security**: Propagating authenticated tenant/principal into all tool invocations.

### Non-Goals
- Financial proposal persistence, lifecycle, or downstream execution (owned by `SPEC-004`).
- Embedding LLM weights, fine-tuning, or GPU management inside the service.
- Transmitting sensitive financial credentials (passwords, PINs, card numbers) via MCP forms (`I-MCP-003`).

---

## 3. Cross-Feature & Invariant Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Side Effect / Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`copilot.domain`** (`SPEC-004`) | Proposal creation via `copilot::api` | Mismatched parameters or unauthenticated caller | `I-AI-004`: Security context propagation; parameters validation |
| **`ledger.api`** | Balance queries (`BalanceUseCase`) | Excess connection holding via long-lived MCP streams | Streamable HTTP with stateless MRTR; non-locking reads |
| **`intelligence.api`** | Projections & subscriptions inquiry | Heavy repeated queries from AI conversational loops | Perimeter rate limiting; ephemeral calculation |
| **`security`** | Agent credential authentication | Client header spoofing | Authenticated principal resolution only; ignore `X-Tenant-Id` |

---

## 4. Mathematical & System Invariants

- **`I-MCP-001` (Transport Adapter Isolation)**:
  MCP transport is strictly an external edge adapter. Domain types and use cases in `br.com.wallet.copilot.api` MUST NOT depend on MCP or Spring AI types.
- **`I-MCP-002` (Elicitation Non-Bypass Gate)**:
  Absence of client elicitation support MUST NEVER result in automatic mutation execution. If elicitation is unsupported, the gateway creates a proposal and returns the external approval URI:
  $$\text{ClientElicitation}(\text{Unsupported}) \implies \text{Status} = \text{PROPOSED} \land \text{RequireExternalApproval}()$$
- **`I-MCP-003` (Zero Sensitive Credentials in MCP)**:
  Authentication credentials, passwords, PINs, and full card PANs MUST NOT be requested via MCP Form Elicitation. Sensitive flows MUST use URL Elicitation or out-of-band channels.
- **`I-MCP-004` (Protocol vs Financial State Decoupling)**:
  MCP interaction state (`CALL` $\to$ `INPUT_REQUIRED` $\to$ `INPUT_RESPONSE`) MUST remain decoupled from durable `FinancialProposal` state (`PROPOSED` $\to$ `EXECUTED`).

---

## 5. Functional Requirements (MoSCoW — `I-SDD-004`)

### 5.1 Must Have (`[MUST]`)
- **`REQ-MCP-001 [MUST]`**: Configure Spring AI MCP Server utilizing Streamable HTTP on Spring Boot 4.2.0-M2 and WebFlux.
- **`REQ-MCP-002 [MUST]`**: Implement Read-Only MCP Tool Catalog:
  - `wallet_get_balance(walletId)` $\to$ queries `ledger.api.BalanceUseCase`.
  - `wallet_get_cashflow_projections(walletId)` $\to$ queries `intelligence.api.CashflowForecastingUseCase`.
  - `wallet_get_active_subscriptions(walletId)` $\to$ queries `intelligence.api.SubscriptionQueryUseCase`.
  - `wallet_simulate_goal_strategy(goalId, targetAmount, targetDate)` $\to$ queries `goals.api.GoalUseCase`.
- **`REQ-MCP-003 [MUST]`**: Implement Mutation Proposal Tools creating `FinancialProposal` via `copilot::api`:
  - `copilot_propose_savings_rule(planId, ruleType, targetRatio, threshold)`
  - `copilot_propose_goal_adjustment(goalId, newTargetAmount, newTargetDate)`
  - `copilot_propose_transfer(sourceWalletId, targetWalletId, amount, description)`
- **`REQ-MCP-004 [MUST]`**: Propagate Authenticated Security Context (`tenantId`, `userId`) to all tool invocations.
- **`REQ-MCP-005 [MUST]`**: Implement Elicitation Capability Negotiation: inspect client handshake; if elicitation is not supported, return proposal details and instructions for external human approval.

### 5.2 Should Have (`[SHOULD]`)
- **`REQ-MCP-006 [SHOULD]`**: Implement interactive Form Elicitation (`confirm_proposal`) on compatible MCP clients.
- **`REQ-MCP-007 [SHOULD]`**: Support Multi-Round-Trip Requests (MRTR) emitting `input_required` without holding open server streams.
- **`REQ-MCP-008 [SHOULD]`**: Implement URL Elicitation redirects for sensitive confirmation workflows.

### 5.3 Could Have (`[COULD]`)
- **`REQ-MCP-009 [COULD]`**: Format financial projection summaries with rich Markdown tables for conversational AI agents.

### 5.4 Won't Have This Time (`[WON'T]`)
- Unilateral financial execution without proposal creation (`I-AI-001`).
- MCP tools modifying internal database tables directly (`I-MODULITH-001`).

---

## 6. Interface Contracts

### 6.1 MCP Tool Schema (Streamable HTTP JSON-RPC)
```json
{
  "name": "copilot_propose_transfer",
  "description": "Propose an asynchronous financial transfer requiring explicit human confirmation",
  "inputSchema": {
    "type": "object",
    "properties": {
      "sourceWalletId": { "type": "string", "format": "uuid" },
      "targetWalletId": { "type": "string", "format": "uuid" },
      "amount": { "type": "number", "minimum": 0.01 },
      "description": { "type": "string" },
      "idempotencyKey": { "type": "string" }
    },
    "required": ["sourceWalletId", "targetWalletId", "amount"]
  }
}
```

### 6.2 MRTR Elicitation Response (`input_required`)
```json
{
  "type": "input_required",
  "proposalId": "e1000000-0000-0000-0000-000000000001",
  "message": "Transfer R$ 50.00 from Wallet A to Wallet B?",
  "schema": {
    "type": "object",
    "properties": {
      "confirmed": { "type": "boolean", "description": "Confirm or reject transfer" }
    },
    "required": ["confirmed"]
  }
}
```

---

## 7. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Boundary / Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-MCP-002` (Read Tools) | Tool returns balance/projections for $(T, W)$ | Invalid UUID or missing parameters $\to$ Schema validation error | Tool queries internal DAOs $\to$ Architecture test failure (`I-MODULITH-001`) |
| `REQ-MCP-003` (Proposals) | Mutation tool generates proposal via `copilot::api` | Negative amount or empty targets $\to$ Tool error response | Tool attempts direct transfer $\to$ Invariant breach (`I-AI-001`) |
| `REQ-MCP-005` (Negotiation) | Client without elicitation gets proposal confirmation | Malformed client capabilities $\to$ Fallback to proposal return | Unsupported elicitation triggers auto-execution $\to$ Breach (`I-MCP-002`) |
| `REQ-MCP-004` (Context) | Authenticated tenant context applied to queries | Missing security context $\to$ Tool authentication failure | Cross-tenant tool inquiry $\to$ 404 / Non-disclosure (`I-AI-004`) |

---

## 8. Acceptance Criteria & Practical Verification (`I-SDD-002`)

### 8.1 Acceptance Criteria
- [ ] All `[MUST]` requirements implemented with unit & integration tests (`./gradlew test`).
- [ ] Modulith architecture tests verify `copilot.internal.mcp` does not violate module boundaries.
- [ ] MCP Streamable HTTP handshake and tools negotiation verified with protocol tests.

### 8.2 Practical Verification Invariants
1. **Transport Isolation**: Domain use cases function completely independently of the MCP server.
2. **Elicitation Non-Bypass**: Inability of client to process elicitation yields a `PROPOSED` entity awaiting external approval.
3. **Protocol Decoupling**: Disconnecting or timing out during MCP MRTR does not alter proposal validity.
