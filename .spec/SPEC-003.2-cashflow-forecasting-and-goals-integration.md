# 📋 Specification: SPEC-003.2 — Forward Cashflow Forecasting & Goals Integration

- **Status**: Ratified
- **Author**: Antigravity Intelligence & Financial Planning Team
- **Date**: 2026-10-07
- **Target Release / Milestone**: Wallet Service V4 — Phase 3.2
- **Bounded Context / Module**: `br.com.wallet.intelligence`
- **Spec Slicing Scope**: Atomic Slice ($\le 250$ lines, `I-SDD-006`)

---

## 0. Pre-Flight History & Context Audit

- **Histories & Summaries Audited**:
  - `.histories/history9.txt`: Established that `br.com.wallet.goals` `CashflowProfile` V1 requires manual inputs and must be fed by Spending Intelligence.
  - `.histories/history84.txt`: Semantic commitment partitioning: separating indefinite subscriptions from finite installments via `SPEC-000.8.1` classification.
  - `.histories/history85.txt` & `history86.txt`: Decoupled capability slice: clean interface with Goals, non-locking balance projections, temporal truth, and precision consistency.
  - `.spec/SPEC-002-financial-goal-engine.md`: Goal feasibility simulation (`GoalStrategyEngine`) relying on `CashflowProfile`.
  - `.spec/SPEC-003.1-recurring-pattern-and-subscription-detection.md`: Active subscription contracts and cadence model.
- **Foundational Constraints (`constitution.md` & `SPEC-003`)**:
  - `I-INTEL-001`: Zero direct ledger mutation.
  - `I-INTEL-003`: Canonical `BigDecimal` scale 2 arithmetic (`RoundingMode.HALF_EVEN`).
  - `I-INTEL-009`: Temporal truth / no future leakage.
  - `I-INTEL-010` & `I-SEC-005`: Strict multi-tenant isolation (`tenant_id VARCHAR(64) NOT NULL`).
  - `I-BALANCE-001`: Read account balance via `BalanceUseCase` projection without write locks.

---

## 1. Intent & Business Value

Without forward liquidity visibility, users risk overdrafts when subscriptions hit an account with low balance. Furthermore, goal contribution calculations in `br.com.wallet.goals` rely on static assumptions. This capability calculates 7, 14, and 30-day forward liability calendars, generates liquidity shortfall warnings, partitions indefinite subscriptions from expiring installments (via `SPEC-000.8.1` semantic classifications), and bridges inferred committed expenses to `CashflowProfile` in the Goals engine with strict multi-tenant isolation.

---

## 2. Scope & Non-Goals

### In Scope
- Forward liability calculation across 7, 14, and 30-day horizons ($L_7, L_{14}, L_{30}$) strictly scoped to `(tenantId, walletId)`.
- Liquidity shortfall evaluation against current balance projection.
- Monthly committed expense normalization with semantic partitioning (indefinite vs. finite obligations).
- Integration with `br.com.wallet.goals.api.CashflowProfileUseCase`.
- REST query endpoints for forecast inspection and goal sync scoped to tenant.

### Non-Goals
- Automated funds sweeping (savings module handles actual sweeps).
- Overdraft loans or debt provisioning.
- Direct balance modification (`I-INTEL-001`).

---

## 3. Mathematical & System Invariants

- **`I-CASH-001` (Forward Liability Accumulation)**:
  For horizon $H \in \{7, 14, 30\}$ days relative to evaluation timestamp $t_0$ for wallet $(T, W)$:
  $$L_H(T, W) = \sum_{S \in \text{ActiveSubscriptions}(T, W), \, t_0 \le \text{NextExpectedAt}(S) \le t_0 + H} S.\text{averageAmount}$$
- **`I-CASH-002` (Liquidity Shortfall Evaluation)**:
  $$\text{Shortfall}_H(T, W) = \max\left(0.00, \, L_H(T, W) - \text{Balance}(T, W)\right)$$
  If $\text{Shortfall}_H(T, W) > 0.00$, the forecast status is `DEFICIT_WARNING`; otherwise `SURPLUS`.
- **`I-CASH-003` (Semantic Committed Expense Partitioning)**:
  Committed expenses are partitioned based on semantic classification (`SPEC-000.8.1`):
  $$\text{Committed}_{\text{monthly}}(T, W) = \sum_{S \in \text{ActiveSubscriptions}(T, W), \, S.\text{classification} \ne \text{INSTALLMENT}} \text{NormalizeMonthly}(S.\text{cadence}, S.\text{averageAmount})$$
  $$\text{NormalizeMonthly}(C, A) = \begin{cases}
  A \times 4.33 & \text{if } C = \text{WEEKLY} \\
  A \times 2.17 & \text{if } C = \text{BI\_WEEKLY} \\
  A \times 1.00 & \text{if } C = \text{MONTHLY} \\
  A / 12.00     & \text{if } C = \text{ANNUAL}
  \end{cases}$$
  *Finite obligations (`INSTALLMENT`) accrue to forward liabilities $L_H$ but are excluded from indefinite `monthlyCommittedExpenses`.*
- **`I-CASH-004` (CashflowProfile Preservation Invariant)**:
  When synchronizing with `br.com.wallet.goals`, `monthlyCommittedExpenses` is updated with $\text{Committed}_{\text{monthly}}(T, W)$ while strictly preserving existing `minimumSafetyBuffer` and user-configured `monthlyIncome`.
- **`I-CASH-005` (Temporal Truth Invariant)**:
  Forward liability projections for horizon $H$ at observation time $t_0$ MUST evaluate only subscriptions and event evidence recorded at or before $t_0$ (`I-INTEL-009`).
- **`I-CASH-006` (Non-Locking Projection Read Invariant)**:
  Balances for shortfall calculation MUST be read via projection query (`BalanceUseCase.getBalance`) without acquiring database row locks (`SELECT FOR UPDATE`).
- **`I-CASH-007` (Strict Tenant Cashflow Partitioning)**:
  Forward liabilities, shortfalls, and goal synchronization MUST be partitioned strictly by `tenantId` (`I-INTEL-010`). Zero cross-tenant data contribution is permitted.

---

## 4. Functional Requirements (MoSCoW Prioritized — `I-SDD-004`)

### 4.1 Must Have (`[MUST]`)
- **`REQ-CASH-001 [MUST]`**: Calculate $L_7, L_{14}, L_{30}$ forward liability projections from active subscriptions matching `(tenantId, walletId)` (`I-CASH-001`, `I-CASH-007`).
- **`REQ-CASH-002 [MUST]`**: Evaluate liquidity shortfall against current balance (`I-CASH-002`) without acquiring database row locks (`I-CASH-006`).
- **`REQ-CASH-003 [MUST]`**: Compute normalized monthly committed expenses, filtering out finite installments (`I-CASH-003`).
- **`REQ-CASH-004 [MUST]`**: Provide REST API `GET /api/v1/intelligence/cashflow/{walletId}/projections` scoped to tenant returning commitments and shortfall status.
- **`REQ-CASH-005 [MUST]`**: Expose `POST /api/v1/intelligence/cashflow/{walletId}/sync-goals` propagating `tenantId` to update `CashflowProfile` in `br.com.wallet.goals` (`I-CASH-004`).

### 4.2 Should Have (`[SHOULD]`)
- **`REQ-CASH-006 [SHOULD]`**: Emit `CashflowShortfallAlertEvent` carrying `tenantId` when 14-day shortfall $\text{Shortfall}_{14}(T, W) > 0.00$.

### 4.3 Could Have (`[COULD]`)
- **`REQ-CASH-007 [COULD]`**: Inferred `monthlyIncome` calculation from recurring `DepositCompletedEvent` credits within the tenant.

### 4.4 Won't Have (`[WON'T]`)
- **`REQ-CASH-008 [WON'T]`**: Direct ledger mutations, sweeps, or balance adjustments (`I-INTEL-001`).
- **`REQ-CASH-009 [WON'T]`**: Overdraft credit line underwriting.

---

## 5. Cross-Feature Impact Matrix (`I-SDD-005`)

| Module | Interaction Flow | Potential Side Effect | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **`goals`** | Calls `CashflowProfileUseCase.saveProfile` | Overwriting user buffer preferences or cross-tenant contamination | `I-CASH-004`: Preserve existing `minimumSafetyBuffer` and `monthlyIncome`. Ensure `tenantId` match. |
| **`ledger`** | Queries `BalanceUseCase.getBalance` | Read contention on accounts table | `I-CASH-006`: Non-locking projection query; zero `SELECT FOR UPDATE`. |

---

## 6. Interface Contracts

### REST API
```http
GET /api/v1/intelligence/cashflow/{walletId}/projections
Response (200 OK):
{
  "tenantId": "tenant-alpha",
  "walletId": "w1000000-0000-0000-0000-000000000001",
  "currentBalance": 150.00,
  "liabilities7Days": 39.90,
  "liabilities14Days": 120.00,
  "liabilities30Days": 250.00,
  "shortfall14Days": 0.00,
  "shortfall30Days": 100.00,
  "status30Days": "DEFICIT_WARNING",
  "normalizedMonthlyCommitted": 200.00,
  "activeInstallmentsCount": 1
}
```

---

## 7. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Boundary / Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-CASH-001` / `002` | Subscriptions totaling R$ 200 in 14 days, balance R$ 150 $\to$ Shortfall R$ 50 | Horizon without subscriptions $\to$ Liabilities R$ 0.00 | Negative balance handling $\to$ Shortfall equals total liability |
| `REQ-CASH-001` / `007` | Liabilities computed for $(T_A, W)$ | Cross-tenant request $\to$ Access denied / 0 liabilities from $T_B$ | Contamination breach $\to$ Subscriptions of $T_B$ ignored (`I-CASH-007`) |
| `REQ-CASH-003` | 1 subscription (R$ 50) + 1 installment (R$ 50) $\to$ Committed R$ 50 (installment excluded) | Zero active subscriptions $\to$ Committed R$ 0.00 | Rounding mode mismatch $\to$ Exact `HALF_EVEN` enforced |
| `REQ-CASH-005` | Sync updates goals `monthlyCommittedExpenses` | Wallet without existing profile $\to$ Profile created with zero income | Sync modifying `minimumSafetyBuffer` $\to$ rejected (`I-CASH-004`) |

---

## 8. Acceptance Criteria (`I-SDD-002`)

- [ ] All `[MUST]` requirements passing with unit and integration tests (`CashflowForecastIT`).
- [ ] Schema migration verified in `schema.sql` (`cashflow_forecasts` table if materialized with `tenant_id`).
- [ ] Multi-tenant isolation verified with zero cross-tenant shortfall or liability bleed (`TenantIsolationIT`).
- [ ] Modulith architecture test passes with 0 cycle violations between `intelligence` and `goals`.
- [ ] Zero Spec-Drift: Implementation matches specification 100% (`I-SDD-003`).
