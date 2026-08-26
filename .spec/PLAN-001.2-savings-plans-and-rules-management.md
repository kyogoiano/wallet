# 🏛️ Architecture & Implementation Plan: PLAN-001.2 — Savings Plans and Dynamic Rules Management

- **Specification Reference**: [`.spec/SPEC-001.2-savings-plans-and-rules-management.md`](file:///.spec/SPEC-001.2-savings-plans-and-rules-management.md)
- **Status**: Approved
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-25
- **Target Release / Milestone**: Wallet Service V4 — Phase 1.2

---

## 1. Architectural Topology & Component Interaction

```mermaid
flowchart TD
    subgraph REST [br.com.wallet.infrastructure.rest]
        SavingsController["SavingsController\n(/savings/*)"]
        SavingsApi["SavingsApi (OpenAPI Contract)"]
        SavingsController -. implements .-> SavingsApi
    end

    subgraph SavingsAPI [br.com.wallet.savings.api]
        SavingsPlanUseCase["SavingsPlanUseCase\n(create, get, pause, resume, delete, addRule, removeRule, toggleRule)"]
        SavingsQueryUseCase["SavingsQueryUseCase\n(getMetrics)"]
    end

    subgraph SavingsInternal [br.com.wallet.savings.internal]
        SavingsPlanService["SavingsPlanService\n(@Service, @Transactional)"]
        SavingsQueryService["SavingsQueryService\n(@Service)"]
        SavingsPlanDao["SavingsPlanDao\n(JDBC)"]
        SavingsRuleDao["SavingsRuleDao\n(JDBC)"]
    end

    subgraph DB [(PostgreSQL)]
        savings_plans["savings_plans"]
        savings_rules["savings_rules"]
    end

    SavingsController --> SavingsPlanUseCase
    SavingsController --> SavingsQueryUseCase
    SavingsPlanService -. implements .-> SavingsPlanUseCase
    SavingsQueryService -. implements .-> SavingsQueryUseCase
    SavingsPlanService --> SavingsPlanDao
    SavingsPlanService --> SavingsRuleDao
    SavingsPlanDao --> savings_plans
    SavingsRuleDao --> savings_rules
```

---

## 2. API Design & Endpoint Details

### 2.1 Savings Plans Endpoints
- `POST /savings/plans`
  - Body: `CreateSavingsPlanCommand` (sourceWalletId, targetWalletId, minimumRetainedBalance, rules)
  - Returns: `201 Created` with `SavingsPlanDto`
- `GET /savings/plans/{planId}`
  - Returns: `200 OK` with `SavingsPlanDto` or `404 Not Found`
- `GET /savings/plans/wallet/{walletId}`
  - Returns: `200 OK` with `List<SavingsPlanDto>`
- `POST /savings/plans/{planId}/pause`
  - Returns: `204 No Content`
- `POST /savings/plans/{planId}/resume`
  - Returns: `204 No Content`
- `DELETE /savings/plans/{planId}`
  - Returns: `204 No Content`

### 2.2 Savings Rules Endpoints
- `POST /savings/plans/{planId}/rules`
  - Body: `CreateSavingsRuleCommand` (ruleType, stepAmount, percentageRate, ceilingThreshold)
  - Returns: `201 Created` with `SavingsRuleDto`
- `GET /savings/plans/{planId}/rules`
  - Returns: `200 OK` with `List<SavingsRuleDto>`
- `PATCH /savings/rules/{ruleId}/status?active={true|false}`
  - Returns: `204 No Content`
- `DELETE /savings/rules/{ruleId}`
  - Returns: `204 No Content`

### 2.3 Savings Metrics Endpoint
- `GET /savings/metrics/{walletId}`
  - Returns: `200 OK` with `SavingsMetricsResponse`

---

## 3. Data Access Layer Extensions

### `SavingsRuleDao`
- `Optional<SavingsRule> findById(UUID id)`
- `void updateActive(UUID id, boolean isActive)`
- `void deleteById(UUID id)`

### `SavingsPlanDao`
- Existing `findById(UUID id)` already queries plan and its rules.
- Existing `updateStatus(UUID id, String status)` updates plan status.

---

## 4. Error Handling Strategy

- When a plan or rule is not found, throw `NoSuchElementException("Plan not found: " + planId)` / `NoSuchElementException("Rule not found: " + ruleId)`.
- Register `NoSuchElementException` in `ApiExceptionHandler` returning `404 Not Found` with `ErrorCode.NOT_FOUND`.
- Rule validation errors throw `IllegalArgumentException` returning `400 Bad Request`.

---

## 5. Security, Fraud & Modulith Boundaries

- Zero direct imports of `br.com.wallet.savings.internal.*` outside `br.com.wallet.savings`.
- Zero direct database access to `ledger` or `accounts` tables from savings domain.
- `infrastructure` interacts with `savings` strictly via `SavingsPlanUseCase` and `SavingsQueryUseCase`.
