# 📋 Specification: SPEC-003.2 — Forward Cashflow Forecasting & Goals Integration

- **Status**: Ratified
- **Author**: Antigravity Intelligence & Financial Planning Team
- **Date**: 2026-10-08
- **Target Release / Milestone**: Wallet Service V4 — Phase 3.2
- **Bounded Context / Module**: `br.com.wallet.intelligence`
- **Spec Slicing Scope**: Atomic Slice ($\le 250$ lines, `I-SDD-006`)

---

## 0. Pre-Flight History & Context Audit

- **Histories & Summaries Audited**:
  - `.histories/history9.txt`: `br.com.wallet.goals` `CashflowProfile` V1 requires intelligence-driven committed expenses.
  - `.histories/history84.txt` to `history88.txt`: Semantic commitment partitioning: indefinite subscriptions vs finite installments.
  - `.histories/history89.txt` & `history91.txt`: Outflow observation contracts and deterministic statistical boundaries.
  - `.histories/history92.txt`: Occurrence-based projection ($O_H$), server $t_0$, anti-double-counting, Goals API boundary, idempotent sync, and ephemeral calculation (no redundant forecast table).
  - `.spec/SPEC-002-financial-goal-engine.md`: Goal feasibility simulation relying on `CashflowProfile`.
  - `.spec/SPEC-003.1-recurring-pattern-and-subscription-detection.md`: Active subscription contracts, cadence model, and tenant isolation.
- **Foundational Constraints (`constitution.md` & `SPEC-003`)**:
  - `I-INTEL-001`: Zero direct ledger mutation.
  - `I-INTEL-003`: Canonical `BigDecimal` scale 2 arithmetic (`RoundingMode.HALF_EVEN`).
  - `I-INTEL-009`: Temporal truth / no future leakage.
  - `I-INTEL-010` & `I-SEC-005`: Strict multi-tenant isolation (`tenant_id VARCHAR(64) NOT NULL`).
  - `I-BALANCE-001`: Read account balance via `BalanceUseCase` projection without write locks.

---

## 1. Intent & Business Value

Without forward liquidity visibility, users risk overdrafts when recurring commitments hit an account with low balance. Furthermore, goal contribution simulations in `br.com.wallet.goals` rely on static assumptions. This capability calculates 7, 14, and 30-day forward liability calendars via projected occurrences, provides current-balance coverage shortfall warnings, partitions indefinite subscriptions from expiring installments, and bridges inferred committed expenses to `CashflowProfile` in Goals with strict multi-tenant isolation.

---

## 2. Scope & Non-Goals

### In Scope
- Forward liability calculation across 7, 14, and 30-day horizons ($L_7, L_{14}, L_{30}$) using projected recurrence occurrence sets $O_H(S, t_0)$ strictly scoped to `(tenantId, walletId)`.
- Current-balance coverage shortfall evaluation against non-locking balance projection.
- Monthly committed expense normalization with semantic partitioning (indefinite subscriptions vs finite installments).
- Ephemeral, deterministic calculation on-demand (no materialized `cashflow_forecasts` table in V1).
- Idempotent synchronization with `br.com.wallet.goals.api.CashflowProfileUseCase`.
- REST query endpoints scoped to authenticated tenant context.

### Non-Goals
- Full future cash balance simulation with predicted deposits/income (deferred to `[COULD]` `REQ-CASH-007`).
- Automated funds sweeping (savings module handles actual sweeps).
- Overdraft credit line underwriting.
- Materialized forecast storage table in PostgreSQL (`I-CASH-008`).

---

## 3. Mathematical & System Invariants

- **`I-CASH-001` (Forward Liability Accumulation via Projected Occurrences)**:
  For horizon $H \in \{7, 14, 30\}$ days relative to server evaluation timestamp $t_0$ for wallet $(T, W)$:
  Forecasting strictly uses the canonical interval associated with the classified cadence ($\Delta t_{\text{cadence}} \in \{7 \text{ (WEEKLY)}, 14 \text{ (BI\_WEEKLY)}, 30 \text{ (MONTHLY)}, 365 \text{ (ANNUAL)}\}$ days), rather than the historical average interval.
  For each $S \in \text{ActiveSubscriptions}(T, W)$:
  If $S.\text{cadence} \in \{\text{WEEKLY}, \text{BI\_WEEKLY}, \text{MONTHLY}, \text{ANNUAL}\}$:
  $$O_H(S, t_0) = \{ t_k = S.\text{nextExpectedAt} + k \cdot \Delta t_{\text{cadence}} \mid k \ge 0, \, t_0 \le t_k \le t_0 + H \}$$
  If $S.\text{cadence} == \text{IRREGULAR}$, $O_H(S, t_0) = \emptyset$ (zero projected occurrences, zero $L_H$ contribution).
  $$L_H(T, W) = \sum_{S \in \text{ActiveSubscriptions}(T, W)} |O_H(S, t_0)| \times S.\text{averageAmount}$$
- **`I-CASH-002` (Current-Balance Coverage Shortfall)**:
  $$\text{Shortfall}_H(T, W) = \max\left(0.00, \, L_H(T, W) - \text{Balance}(T, W)\right)$$
  If $\text{Shortfall}_H(T, W) > 0.00$, the forecast status is `DEFICIT_WARNING`; otherwise `SURPLUS`.
  *Shortfall represents an immediate coverage warning of balance observed at $t_0$ against upcoming obligations, not a full future cash simulation.*
- **`I-CASH-003` (Semantic Committed Expense Partitioning & Anti-Double-Counting)**:
  $$\text{Committed}_{\text{monthly}}(T, W) = \sum_{S \in \text{ActiveSubscriptions}(T, W), \, S.\text{classification} \ne \text{"INSTALLMENT"}} \text{NormalizeMonthly}(S.\text{cadence}, S.\text{averageAmount})$$
  $$\text{NormalizeMonthly}(C, A) = \begin{cases}
  A \times 4.33 & \text{if } C = \text{WEEKLY} \\
  A \times 2.17 & \text{if } C = \text{BI\_WEEKLY} \\
  A \times 1.00 & \text{if } C = \text{MONTHLY} \\
  A / 12.00     & \text{if } C = \text{ANNUAL} \\
  0.00          & \text{if } C = \text{IRREGULAR}
  \end{cases}$$
  *Finite obligations (`INSTALLMENT`) accrue to forward liabilities $L_H$ while active, but are excluded from indefinite `monthlyCommittedExpenses`. $L_H$ and $\text{Committed}_{\text{monthly}}$ serve distinct functions and MUST NEVER be summed together.*
- **`I-CASH-004` (Goals Ownership & Profile Preservation Invariant)**:
  `br.com.wallet.goals` owns `CashflowProfile`. `intelligence` interacts strictly via `goals::api` (`CashflowProfileUseCase`), never touching Goals tables directly. Resolves `userId` via `AccountUseCase.find(walletId).userId()` (`ledger::api`). When syncing, updates `monthlyCommittedExpenses` while strictly preserving existing `minimumSafetyBuffer` and user-configured `monthlyIncome`. If no profile exists, initializes with 0.00 income and 0.00 buffer.
- **`I-CASH-005` (Server Evaluation Timestamp $t_0$ & Temporal Truth)**:
  $t_0$ is defined as the server-side evaluation `Instant` (via injected `Clock`). $t_0$ is strictly NOT client-controllable in public REST queries. Projections evaluate only data recorded at or before $t_0$ (`I-INTEL-009`).
- **`I-CASH-006` (Non-Locking Projection Read Invariant)**:
  Balances for shortfall calculation MUST be read via projection query (`BalanceUseCase.getBalance`) without acquiring database row locks (`SELECT FOR UPDATE`). Zero ledger mutation (`I-INTEL-001`).
- **`I-CASH-007` (Strict Authenticated Tenant Partitioning)**:
  Tenant scope is resolved strictly from the authenticated security context (`TenantContextHolder`). Client-controlled tenant headers/parameters are ignored. Cross-tenant access is rejected.
- **`I-CASH-008` (Ephemeral Deterministic Calculation)**:
  In Phase 3.2, cashflow forecast is computed deterministically on-demand from active subscriptions and balance projections. No `cashflow_forecasts` table is materialized in database schema.

---

## 4. Functional Requirements (MoSCoW Prioritized — `I-SDD-004`)

### 4.1 Must Have (`[MUST]`)
- **`REQ-CASH-001 [MUST]`**: Calculate $L_7, L_{14}, L_{30}$ forward liabilities using projected recurrence occurrences $O_H(S, t_0)$ from active subscriptions matching `(tenantId, walletId)` (`I-CASH-001`, `I-CASH-007`).
- **`REQ-CASH-002 [MUST]`**: Evaluate current-balance coverage shortfall against balance projection (`I-CASH-002`) without database row locks (`I-CASH-006`).
- **`REQ-CASH-003 [MUST]`**: Compute normalized monthly committed expenses, filtering out finite installments (`I-CASH-003`).
- **`REQ-CASH-004 [MUST]`**: Expose REST API `GET /api/v1/intelligence/cashflow/{walletId}/projections` using server $t_0$ and authenticated tenant context (`I-CASH-005`, `I-CASH-007`, `I-CASH-008`).
- **`REQ-CASH-005 [MUST]`**: Expose idempotent `POST /api/v1/intelligence/cashflow/{walletId}/sync-goals` propagating `tenantId` to update `CashflowProfile` in `br.com.wallet.goals` (`I-CASH-004`).

### 4.2 Should Have (`[SHOULD]`)
- **`REQ-CASH-006 [SHOULD]`**: Emit `CashflowShortfallAlertEvent` when transition from `SURPLUS` to `DEFICIT_WARNING` is detected during evaluation/sync.

### 4.3 Could Have (`[COULD]`)
- **`REQ-CASH-007 [COULD]`**: Inferred `monthlyIncome` calculation from recurring `DepositCompletedEvent` credits within tenant.

### 4.4 Won't Have (`[WON'T]`)
- **`REQ-CASH-008 [WON'T]`**: Direct ledger mutations, sweeps, or balance adjustments (`I-INTEL-001`).
- **`REQ-CASH-009 [WON'T]`**: Overdraft credit underwriting or materialized forecast database tables (`I-CASH-008`).

---

## 5. Cross-Feature Impact Matrix (`I-SDD-005`)

| Module | Interaction Flow | Potential Side Effect | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **`goals`** | Calls `CashflowProfileUseCase.saveCashflowProfile` | Overwriting user buffer or cross-tenant contamination | `I-CASH-004`: Preserve existing buffer and income. Ensure `tenantId` match. Strictly use `goals::api`. |
| **`ledger`** | Queries `BalanceUseCase.getBalance` & `AccountUseCase.find` | Read contention on accounts table | `I-CASH-006`: Non-locking projection query; zero `SELECT FOR UPDATE`. |

---

## 6. Interface Contracts

### REST API
```http
GET /api/v1/intelligence/cashflow/{walletId}/projections
Response (200 OK):
{
  "tenantId": "tenant-alpha",
  "walletId": "a1000000-0000-0000-0000-000000000001",
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

POST /api/v1/intelligence/cashflow/{walletId}/sync-goals
Response (200 OK):
{
  "walletId": "a1000000-0000-0000-0000-000000000001",
  "userId": "b1000000-0000-0000-0000-000000000001",
  "monthlyCommittedExpenses": 200.00,
  "monthlyIncome": 3000.00,
  "minimumSafetyBuffer": 500.00,
  "synced": true
}
```

---

## 7. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Boundary / Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-CASH-001` / `002` | Weekly subscription R$ 50 in 30 days $\to$ 4 occurrences $\to$ Liabilities R$ 200 | Horizon without occurrences $\to$ Liabilities R$ 0.00 | Single occurrence counted for weekly $\to$ rejected (`I-CASH-001`) |
| `REQ-CASH-001` / `007` | Liabilities computed for $(T_A, W)$ | Cross-tenant request $\to$ Access denied | Subscriptions of $T_B$ leak into $T_A \to$ rejected (`I-CASH-007`) |
| `REQ-CASH-003` | 1 subscription (R$ 50) + 1 installment (R$ 50) $\to$ Committed R$ 50 (installment excluded) | Zero active subscriptions $\to$ Committed R$ 0.00 | Installment included in committed $\to$ rejected (`I-CASH-003`) |
| `REQ-CASH-005` | Sync updates goals `monthlyCommittedExpenses` idempotently | Wallet without profile $\to$ Profile created with 0 income/buffer | Sync modifying existing `minimumSafetyBuffer` $\to$ rejected (`I-CASH-004`) |

---

## 8. Acceptance Criteria (`I-SDD-002`)

- [ ] All `[MUST]` requirements passing with unit and integration tests (`CashflowForecastIT`).
- [ ] Ephemeral calculation verified with 0 schema migrations for forecast tables (`I-CASH-008`).
- [ ] Multi-tenant isolation verified with zero cross-tenant shortfall or liability bleed (`TenantIsolationIT`).
- [ ] Modulith architecture test passes with 0 cycle violations between `intelligence` and `goals`.
- [ ] Zero Spec-Drift: Implementation matches specification 100% (`I-SDD-003`).
