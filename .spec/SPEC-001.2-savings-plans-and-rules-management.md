# 📋 Specification: SPEC-001.2 — Savings Plans and Dynamic Savings Rules Management

- **Status**: Ratified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-25
- **Target Release / Milestone**: Wallet Service V4 — Phase 1.2
- **Architectural Mantra**: *"Capabilities observe, analyze, and decide. The Financial Core authorizes and executes."*

---

## 1. Intent & Business Value

Enable dynamic lifecycle management for **Savings Plans** and **Savings Rules** via published Use Case interfaces and REST API endpoints (`/savings/*`). 

Previously, savings rules could only be defined statically during initial savings plan creation, and querying plans did not clearly distinguish between source and target wallets or provide a paginated list of all plans. This enhancement provides full runtime capabilities to:
1. Create a savings plan without mandatory rules.
2. Dynamically add savings rules (`ROUND_UP`, `PERCENTAGE`, `THRESHOLD`) to an existing plan at any time.
3. Query, enable, disable, and delete specific savings rules independently.
4. List all savings plans with pagination (`GET /savings/plans?limit=100&offset=0`).
5. Specifically query savings plans by **Source Wallet ID** (`/savings/plans/source/{sourceWalletId}`) and by **Target Wallet ID** (`/savings/plans/target/{targetWalletId}`), in addition to general wallet lookup (`/savings/plans/wallet/{walletId}`).
6. Expose unified REST API endpoints in the infrastructure layer to manage savings plans and rules for clients and automated agents.

---

## 2. Scope & Non-Goals

### In Scope
- **Dynamic Rule Provisioning**: Adding savings rules to existing savings plans (`POST /savings/plans/{planId}/rules`).
- **Rule Lifecycle Management**: Toggling active state (`PATCH /savings/rules/{ruleId}/status`) and deleting rules (`DELETE /savings/rules/{ruleId}`).
- **Plan Management API**: Creating, fetching, pausing, resuming, and deleting savings plans (`/savings/plans/*`).
- **Paginated Listing**: Listing all savings plans with pagination (`GET /savings/plans?limit=100&offset=0`).
- **Source vs Target Wallet Queries**: Distinct query endpoints for source wallet (`/savings/plans/source/{sourceWalletId}`) and target wallet (`/savings/plans/target/{targetWalletId}`).
- **Metrics Exposure**: Fetching savings metrics via REST API (`GET /savings/metrics/{walletId}`).
- **Module Boundary Compliance**: Adherence to Spring Modulith contracts (`infrastructure` $\rightarrow$ `savings::api`).

### Non-Goals
- Modifying core ledger execution or event observation logic (`SavingsEventListener` and `SavingsExecutionService` remain unchanged).

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
- **`REQ-SAV-012` (Plan & Rule Retrieval)**: The system SHALL provide endpoints to retrieve a savings plan by ID (`GET /savings/plans/{planId}`) with its associated rules, list all plans with pagination (`GET /savings/plans?limit=100&offset=0`), query by source wallet (`GET /savings/plans/source/{sourceWalletId}`), query by target wallet (`GET /savings/plans/target/{targetWalletId}`), and query by general wallet (`GET /savings/plans/wallet/{walletId}`).
- **`REQ-SAV-013` (REST API Contract)**: The infrastructure layer SHALL expose `SavingsApi` & `SavingsController` under `/savings` with OpenAPI annotations.
- **`REQ-SAV-014` (Savings Metrics Endpoint)**: The system SHALL expose `GET /savings/metrics/{walletId}` delegating to `SavingsQueryUseCase`.

---

## 5. Interface Contracts

### 5.1 Use Case Interface (`br.com.wallet.savings.api.SavingsPlanUseCase`)

```java
public interface SavingsPlanUseCase {
    SavingsPlanDto createPlan(CreateSavingsPlanCommand command);
    SavingsPlanDto getPlan(UUID planId);
    List<SavingsPlanDto> listPlans(Integer limit, Integer offset);
    List<SavingsPlanDto> getPlansBySourceWallet(UUID sourceWalletId);
    List<SavingsPlanDto> getPlansByTargetWallet(UUID targetWalletId);
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

### 5.2 REST Endpoints (`/savings`)

```http
POST   /savings/plans                           -> Create a savings plan
GET    /savings/plans                           -> List all savings plans (paginated ?limit=100&offset=0)
GET    /savings/plans/{planId}                  -> Get savings plan with rules
GET    /savings/plans/source/{sourceWalletId}   -> Get plans by source wallet
GET    /savings/plans/target/{targetWalletId}   -> Get plans by target wallet
GET    /savings/plans/wallet/{walletId}         -> Get all plans for wallet (source or target)
POST   /savings/plans/{planId}/pause            -> Pause plan
POST   /savings/plans/{planId}/resume           -> Resume plan
DELETE /savings/plans/{planId}                  -> Delete plan
POST   /savings/plans/{planId}/rules            -> Add rule to existing plan
GET    /savings/plans/{planId}/rules            -> List rules for plan
PATCH  /savings/rules/{ruleId}/status           -> Enable/disable rule (?active=true|false)
DELETE /savings/rules/{ruleId}                  -> Delete rule
GET    /savings/metrics/{walletId}              -> Get accumulated savings metrics
```

---

## 6. Seed Data Specifications

The application initializes deterministic seed data for local development, API exploration, and QA testing across `src/main/resources/data.sql` and `docker/init/schema.sql`:

1. **Seeded Wallets**:
   - `0a35fb14-75ee-4125-943b-500893c30d33` (User A Main Wallet, Balance: 10,000.00, ACTIVE)
   - `1b46fc25-86ff-5236-a54c-611904d41e44` (User A Savings Vault, Balance: 0.00, ACTIVE)
   - `2c57ad36-97aa-6347-b65d-722015e52f55` (User B Secondary Wallet, Balance: 5,000.00, ACTIVE)
   - `3d68be47-08bb-7458-c76e-833126f63a66` (User C Blocked Wallet, Balance: 1,000.00, BLOCKED)

2. **Seeded Savings Plans**:
   - **Plan 1 (`d1111111-1111-1111-1111-111111111111`)**:
     - Source: `0a35fb14-75ee-4125-943b-500893c30d33` $\rightarrow$ Target: `1b46fc25-86ff-5236-a54c-611904d41e44`
     - Status: `ACTIVE`, Min Retained: 100.00
     - Rules:
       - `ROUND_UP` (step = 5.00)
       - `PERCENTAGE` (rate = 10.00%)
       - `THRESHOLD` (ceiling = 5,000.00)
   - **Plan 2 (`d2222222-2222-2222-2222-222222222222`)**:
     - Source: `2c57ad36-97aa-6347-b65d-722015e52f55` $\rightarrow$ Target: `1b46fc25-86ff-5236-a54c-611904d41e44`
     - Status: `ACTIVE`, Min Retained: 500.00
     - Rules:
       - `ROUND_UP` (step = 10.00)
       - `PERCENTAGE` (rate = 5.00%)
   - **Plan 3 (`d3333333-3333-3333-3333-333333333333`)**:
     - Source: `0a35fb14-75ee-4125-943b-500893c30d33` $\rightarrow$ Target: `2c57ad36-97aa-6347-b65d-722015e52f55`
     - Status: `PAUSED`, Min Retained: 1,000.00
     - Rules:
       - `THRESHOLD` (ceiling = 8,000.00)
