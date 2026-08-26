# 📋 Specification: SPEC-001.2 — Savings Plans and Dynamic Savings Rules Management

- **Status**: Ratified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-25
- **Target Release / Milestone**: Wallet Service V4 — Phase 1.2
- **Architectural Mantra**: *"Capabilities observe, analyze, and decide. The Financial Core authorizes and executes."*

---

## 1. Intent & Business Value

Enable dynamic lifecycle management for **Savings Plans** and **Savings Rules** via published Use Case interfaces and REST API endpoints (`/savings/*`). 

Previously, savings rules could only be defined statically during initial savings plan creation. This enhancement provides full runtime capabilities to:
1. Create a savings plan without mandatory rules.
2. Dynamically add savings rules (`ROUND_UP`, `PERCENTAGE`, `THRESHOLD`) to an existing plan at any time.
3. Query, enable, disable, and delete specific savings rules independently.
4. Expose unified REST API endpoints in the infrastructure layer to manage savings plans and rules for clients and automated agents.

---

## 2. Scope & Non-Goals

### In Scope
- **Dynamic Rule Provisioning**: Adding savings rules to existing savings plans (`POST /savings/plans/{planId}/rules`).
- **Rule Lifecycle Management**: Toggling active state (`PATCH /savings/rules/{ruleId}/status`) and deleting rules (`DELETE /savings/rules/{ruleId}`).
- **Plan Management API**: Creating, fetching, pausing, resuming, and deleting savings plans (`/savings/plans/*`).
- **Metrics Exposure**: Fetching savings metrics via REST API (`GET /savings/metrics/{walletId}`).
- **Module Boundary Compliance**: Adherence to Spring Modulith contracts (`infrastructure` $\rightarrow$ `savings::api`).

### Non-Goals
- Modifying core ledger execution or event observation logic (`SavingsEventListener` and `SavingsExecutionService` remain unchanged).
- Mutating database schema (existing `savings_plans` and `savings_rules` tables already support these operations).

---

## 3. Mathematical & System Invariants

- **`I-SAVINGS-001` (Deterministic Idempotency & Origin Loop Prevention)**: Savings sweeps dispatched to `ledger.api.TransferFundsUseCase` maintain deterministic operation IDs and `OperationOrigin.SAVINGS_AUTOMATION`.
- **`I-SAVINGS-002` (Zero Direct Ledger Mutation)**: All plan and rule management operations only modify capability state in `savings_plans` and `savings_rules`; they never directly touch `ledger` or `accounts` tables.
- **`I-SAV-RULE-001` (Rule Parameter Validity)**:
  - `ROUND_UP`: `stepAmount > 0.00`
  - `PERCENTAGE`: $0.00 < \text{percentageRate} \le 100.00$
  - `THRESHOLD`: $\text{ceilingThreshold} > 0.00$
- **`I-MODULITH-001` & `I-MODULITH-002`**: REST Controllers in `infrastructure` access the savings capability exclusively through `br.com.wallet.savings.api.*` interfaces.

---

## 4. Functional Requirements

- **`REQ-SAV-010` (Dynamic Rule Addition)**: The system SHALL allow adding one or more savings rules to an existing active or paused savings plan via `SavingsPlanUseCase.addRule(planId, command)` and `POST /savings/plans/{planId}/rules`.
- **`REQ-SAV-011` (Rule State Toggle & Deletion)**: The system SHALL allow enabling/disabling a savings rule (`toggleRule`) and deleting a rule (`removeRule`) by its unique `ruleId`.
- **`REQ-SAV-012` (Plan & Rule Retrieval)**: The system SHALL provide endpoints to retrieve a savings plan by ID (`GET /savings/plans/{planId}`) with its associated rules, and list all plans for a wallet (`GET /savings/plans/wallet/{walletId}`).
- **`REQ-SAV-013` (REST API Contract)**: The infrastructure layer SHALL expose `SavingsApi` & `SavingsController` under `/savings` with OpenAPI annotations.
- **`REQ-SAV-014` (Savings Metrics Endpoint)**: The system SHALL expose `GET /savings/metrics/{walletId}` delegating to `SavingsQueryUseCase`.

---

## 5. Non-Functional Requirements

- **Performance**: Rule lookups and updates SHALL execute in $< 5\text{ms}$ with single-query row-level index access.
- **Modularity**: 100% compliance with Spring Modulith boundaries (`ApplicationModules.verify()`).
- **Test Coverage**: $\ge 85\%$ line coverage across savings service and REST controllers.

---

## 6. Interface Contracts

### 6.1 Use Case Interface (`br.com.wallet.savings.api.SavingsPlanUseCase`)

```java
public interface SavingsPlanUseCase {
    SavingsPlanDto createPlan(CreateSavingsPlanCommand command);
    SavingsPlanDto getPlan(UUID planId);
    List<SavingsPlanDto> getPlansForWallet(UUID walletId);
    void pausePlan(UUID planId);
    void resumePlan(UUID planId);
    void deletePlan(UUID planId);
    SavingsRuleDto addRule(UUID planId, CreateSavingsRuleCommand command);
    void removeRule(UUID ruleId);
    void toggleRule(UUID ruleId, boolean isActive);
    List<SavingsRuleDto> getRulesForPlan(UUID planId);
}
```

### 6.2 REST Endpoints (`/savings`)

```http
POST   /savings/plans                   -> Create a savings plan
GET    /savings/plans/{planId}          -> Get savings plan with rules
GET    /savings/plans/wallet/{walletId} -> Get all plans for wallet
POST   /savings/plans/{planId}/pause    -> Pause plan
POST   /savings/plans/{planId}/resume   -> Resume plan
DELETE /savings/plans/{planId}          -> Delete plan
POST   /savings/plans/{planId}/rules    -> Add rule to existing plan
GET    /savings/plans/{planId}/rules    -> List rules for plan
PATCH  /savings/rules/{ruleId}/status   -> Enable/disable rule (?active=true|false)
DELETE /savings/rules/{ruleId}          -> Delete rule
GET    /savings/metrics/{walletId}      -> Get accumulated savings metrics
```

---

## 7. Acceptance Criteria

- [ ] `SavingsPlanUseCase` updated with `getPlan`, `addRule`, `removeRule`, `toggleRule`, and `getRulesForPlan`.
- [ ] `SavingsRuleDao` updated with `findById`, `deleteById`, and `updateActive`.
- [ ] `SavingsPlanService` implements all new use case methods with strict validation.
- [ ] `SavingsApi` interface and `SavingsController` created in `br.com.wallet.infrastructure.rest`.
- [ ] Unit tests for `SavingsPlanService` and `SavingsController` written and passing.
- [ ] `ModulithArchitectureTest.verifyArchitecture()` passes with 0 violations.
