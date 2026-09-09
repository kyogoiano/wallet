# 📋 Specification: SPEC-002 — Financial Goal & Cashflow Strategy Engine (br.com.wallet.goals)

- **Status**: Ratified (Lean Architecture Approved)
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-27
- **Target Release / Milestone**: Wallet Service V4 — Phase 2 (Goal & Strategy Capability Module)
- **Architectural Mantra**: *"Capabilities observe, analyze, decide, and propose. Wallet Core authorizes and executes."*
- **Core Principle**: *"Savings executes deterministic rules. Goals calculates and simulates financial strategy."*

---

## 1. Intent & Business Value

The **Financial Goal & Cashflow Strategy Engine** (`br.com.wallet.goals`) introduces proactive financial reasoning, temporal cashflow planning, and feasibility simulation to the Wallet Service without accidental complexity or overengineering.

Operating as an autonomous, lean Spring Modulith application module, `br.com.wallet.goals`:
1. **Models Financial Milestones**: Captures user goals with target amounts, target dates, and priority tiers.
2. **Models User Cashflow**: Stores a lightweight planning profile (`CashflowProfile`: income, committed expenses, safety buffer).
3. **Calculates Strategies On-Demand**: Evaluates required contribution rates and safe capacity on-the-fly ($< 1\text{ms}$) against real-time projected balances (`BalanceUseCase`).
4. **Determines Objective Feasibility**: Deterministically classifies goal health (`ON_TRACK`, `AT_RISK`, `UNACHIEVABLE`, `ACHIEVED`).
5. **Simulates Stateless Scenarios**: Exposes an instant mathematical simulation endpoint without database persistence.
6. **Resolves Multi-Goal Prioritization**: Computes waterfall allocation across competing goals based on priority tiers (`CRITICAL` $\succ$ `HIGH` $\succ$ `MEDIUM` $\succ$ `LOW`).

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Lean Goal Engine Architecture                        │
│                                                                        │
│   1. Goal & Profile Storage         2. Pure Deterministic Engine       │
│   ┌───────────────────────────┐    ┌─────────────────────────────────┐ │
│   │ • goals                   │    │ • ContributionCalculator        │ │
│   │ • cashflow_profiles       │    │ • CashflowCapacityCalculator    │ │
│   └─────────────┬─────────────┘    │ • GoalStrategyEngine            │ │
│                 │                  └────────────────┬────────────────┘ │
│                 │                                   │                  │
│                 ▼                                   ▼                  │
│          GoalQueryService  ◄────────────────────────┘                  │
│          (Live Balances via BalanceUseCase)                            │
│                 │                                                      │
│                 ▼                                                      │
│          REST Endpoints (/goals, /goals/cashflow, /goals/simulate)     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Scope & Non-Goals

### In Scope
- **Goal Lifecycle Management**: Create, update, pause, resume, cancel, and mark achieved goals (`FinancialGoal`).
- **Cashflow Profile Management**: Create, update, and query user cashflow parameters (`CashflowProfile`).
- **On-Demand Strategy Calculation**: Dynamic evaluation using live account balance from `BalanceUseCase`.
- **Deterministic Feasibility Categorization**: `ON_TRACK`, `AT_RISK`, `UNACHIEVABLE`, `ACHIEVED`.
- **Stateless Strategy Simulation**: Simulation endpoint evaluating hypothetical goals and cashflows without database writes.
- **Multi-Goal Waterfall Priority**: Distribution of available cashflow capacity in descending priority order.
- **REST API Endpoints**: Full CRUD, cashflow profile, strategy query, and simulation endpoints under `/goals/*`.

### Non-Goals (Lean Boundaries)
- **Direct Ledger Mutation / Auto-Sweeps**: Goals calculates and recommends strategies; it does not execute automated account transfers in V1 (`I-GOAL-001`).
- **Heavy Evaluation History Tables**: No background periodic writing of evaluations to the database; strategies are computed on-the-fly from live state.
- **Autonomous Transaction Categorization**: Automatic deduction of income/expenses from transaction history (reserved for Phase 3: `SPEC-003 Spending Intelligence`).
- **Non-Deterministic AI Heuristics**: All mathematical projections use exact Java `BigDecimal` formulas.

---

## 2.1 Capability Synergies & Cross-Module Roadmap Bridges

Operating under the mantra *"Capabilities observe, analyze, decide, and propose. Wallet Core authorizes and executes"*, the Goal Engine serves as the proactive planning hub for other capabilities:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Capability Synergies Matrix                     │
│                                                                        │
│   Phase 3: SPEC-003                       Phase 2: SPEC-002            │
│  ┌───────────────────────┐               ┌───────────────────────────┐ │
│  │ Subscription & Spend  │ ────────────> │ Goals & Strategy Engine   │ │
│  │ (Auto-detects income  │ Inferred Cash │ (Calculates pacing,       │ │
│  │  & recurring expenses)│ flow Profile  │  deficit, & feasibility)  │ │
│  └───────────────────────┘               └─────────────┬─────────────┘ │
│                                                        │               │
│                                           Proposed     │ Target        │
│                                           Pacing Plan  │ Wallet Sweeps │
│                                                        ▼               │
│                                          ┌───────────────────────────┐ │
│                                          │ Phase 1: SPEC-001         │ │
│                                          │ Smart Savings Rules       │ │
│                                          │ (Automated execution)     │ │
│                                          └───────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Bridge with Smart Savings (`SPEC-001`)**:
   - `FinancialGoal` specifies `targetWalletId` (an account designated for the goal).
   - The Goal Engine calculates `recommendedMonthlyContribution`.
   - In integrated flows, this strategy proposal maps directly to a `SavingsPlan` in `br.com.wallet.savings` with matching target wallet and monthly/percentage rules, bridging mathematical reasoning to automated execution.

2. **Bridge with Spending & Subscription Intelligence (`SPEC-003`)**:
   - In V1, `CashflowProfile` is provisioned manually via `PUT /goals/cashflow`.
   - In `SPEC-003`, recurring transaction analysis automatically detects salary/deposit frequency (`monthlyIncome`) and subscription/bill commitments (`monthlyCommittedExpenses`), continuously feeding the Goal Engine with real-world cashflow reality.

3. **Bridge with AI Financial Copilot & MCP (`SPEC-004`)**:
   - `POST /goals/simulate` and `GET /goals/{id}/strategy` provide pure, deterministic REST contracts ideal for AI tool calling via the Model Context Protocol (MCP).
   - The Copilot can propose goal adjustments and savings plan configurations to users with strict human-in-the-loop approval (`I-AI-001`).

---

## 3. Core Domain Models & Concepts

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ FinancialGoal                                                                          │
│ ────────────────────────────────────────────────────────────────────────────────────── │
│ • id: UUID                                                                             │
│ • userId: UUID                                                                         │
│ • walletId: UUID (Main Account)                                                        │
│ • targetWalletId: UUID (Optional dedicated goal savings wallet)                        │
│ • name: String (e.g. "Emergency Fund 2027")                                            │
│ • targetAmount: BigDecimal (Scale 2, > 0)                                              │
│ • targetDate: LocalDate (Future Date)                                                  │
│ • priority: GoalPriority (LOW | MEDIUM | HIGH | CRITICAL)                              │
│ • status: GoalStatus (ACTIVE | PAUSED | ACHIEVED | CANCELLED)                          │
│ • createdAt / updatedAt: Instant                                                       │
└────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────┐
│ CashflowProfile                                                                        │
│ ────────────────────────────────────────────────────────────────────────────────────── │
│ • id: UUID                                                                             │
│ • userId: UUID                                                                         │
│ • walletId: UUID (Unique per wallet)                                                   │
│ • monthlyIncome: BigDecimal (>= 0)                                                     │
│ • monthlyCommittedExpenses: BigDecimal (>= 0)                                          │
│ • minimumSafetyBuffer: BigDecimal (>= 0)                                               │
│ • updatedAt: Instant                                                                   │
└────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────┐
│ GoalStrategy (Calculated Result)                                                       │
│ ────────────────────────────────────────────────────────────────────────────────────── │
│ • goalId: UUID (or null if simulation)                                                 │
│ • targetAmount: BigDecimal                                                             │
│ • currentAccumulatedAmount: BigDecimal                                                 │
│ • remainingDeficit: BigDecimal                                                         │
│ • remainingMonths: Long                                                                │
│ • requiredMonthlyContribution: BigDecimal                                              │
│ • safeMonthlyContributionCapacity: BigDecimal                                          │
│ • recommendedMonthlyContribution: BigDecimal                                           │
│ • feasibility: GoalFeasibility (ON_TRACK | AT_RISK | UNACHIEVABLE | ACHIEVED)          │
│ • projectedCompletionDate: LocalDate (or null if unachievable)                         │
│ • evaluationDate: LocalDate                                                            │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Mathematical & System Invariants

- **`I-GOAL-001` (Strategy Is Not Execution — Zero Direct Ledger Mutation)**:
  The `goals` module SHALL calculate, recommend, and simulate financial contribution strategies. A strategy calculation MUST NOT mutate account balances, ledger entries, or execute transfers. Direct database access to `accounts` or `ledger` is forbidden (`I-MODULITH-001`).

- **`I-GOAL-002` (Liquidity Preservation & Safety Buffer)**:
  The strategy engine SHALL NEVER recommend a monthly contribution that exceeds safe cashflow capacity or violates the safety liquidity buffer:
  $$\text{netDisposableIncome} = \max\left(0, \text{monthlyIncome} - \text{monthlyCommittedExpenses}\right)$$
  $$\text{safeContributionCapacity} = \max\left(0, \text{netDisposableIncome} - \text{minimumSafetyBuffer}\right)$$
  $$\text{recommendedMonthlyContribution} = \min\left(\text{requiredMonthlyContribution}, \text{safeContributionCapacity}\right)$$

- **`I-GOAL-003` (Deterministic Pure-Function Calculation)**:
  For any identical set of inputs:
  $$\text{Strategy} = f(\text{goal}, \text{currentBalance}, \text{cashflowProfile}, \text{evaluationDate})$$
  The strategy engine MUST return the exact same mathematical output with zero hidden state, zero external I/O, and zero dependency on unseeded randomness or LLM calls.

- **`I-GOAL-004` (Temporal Determinism & Explicit Evaluation Date)**:
  The strategy engine SHALL receive the evaluation moment explicitly (`evaluationDate: LocalDate`) rather than calling `LocalDate.now()` internally, ensuring 100% testability.

- **`I-GOAL-005` (Canonical Monetary Arithmetic & Rounding)**:
  All monetary calculations, deficits, rates, and capacities SHALL use canonical `BigDecimal` with 2 decimal places and `RoundingMode.HALF_EVEN`. Floating-point arithmetic is strictly prohibited.

- **`I-GOAL-006` (Multi-Goal Waterfall Priority Ranking)**:
  When evaluating multiple active goals for a single wallet, available `safeContributionCapacity` SHALL be allocated according to priority ranking:
  $$\text{Priority Ranking}: \text{CRITICAL} (1) \succ \text{HIGH} (2) \succ \text{MEDIUM} (3) \succ \text{LOW} (4)$$
  Unallocated capacity cascades to lower-priority goals.

---

## 5. Functional Requirements

### 5.1 Goal Lifecycle & Management
- **`REQ-GOAL-001` (Goal Creation)**: The system SHALL allow creating a goal with `userId`, `walletId`, optional `targetWalletId`, `name`, `targetAmount` ($> 0$), `targetDate` ($> \text{evaluationDate}$), `priority` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), and initial status `ACTIVE`.
- **`REQ-GOAL-002` (Goal Modification)**: The system SHALL permit updating `name`, `targetAmount`, `targetDate`, and `priority` on active/paused goals.
- **`REQ-GOAL-003` (Pause & Resume)**: The system SHALL support pausing an active goal and resuming a paused goal.
- **`REQ-GOAL-004` (Goal Cancellation & Completion)**: The system SHALL support cancelling a goal, or marking a goal as `ACHIEVED` when $\text{currentAccumulated} \ge \text{targetAmount}$.
- **`REQ-GOAL-005` (Goal Querying)**: The system SHALL allow querying goals by ID and listing all goals for a wallet, filtered by status/priority.

### 5.2 Cashflow Profile Management
- **`REQ-GOAL-006` (Cashflow Configuration)**: The system SHALL permit saving and updating a wallet's `CashflowProfile` (`monthlyIncome`, `monthlyCommittedExpenses`, `minimumSafetyBuffer`).
- **`REQ-GOAL-007` (Cashflow Querying)**: The system SHALL allow retrieving the current `CashflowProfile` for a given wallet.

### 5.3 Deterministic Strategy Engine & Feasibility
- **`REQ-GOAL-008` (Single Goal Strategy Calculation)**: For a goal, current balance, and cashflow profile at `evaluationDate`:
  - $\text{remainingDeficit} = \max(0, \text{targetAmount} - \text{currentBalance})$
  - $\text{remainingMonths} = \max(1, \text{ChronoUnit.MONTHS.between}(\text{evaluationDate}, \text{targetDate}))$
  - $\text{requiredMonthlyContribution} = \frac{\text{remainingDeficit}}{\text{remainingMonths}}$
  - $\text{safeMonthlyContributionCapacity} = \max(0, \text{monthlyIncome} - \text{monthlyCommittedExpenses} - \text{minimumSafetyBuffer})$
  - $\text{recommendedMonthlyContribution} = \min(\text{requiredMonthlyContribution}, \text{safeMonthlyContributionCapacity})$
- **`REQ-GOAL-009` (Feasibility State Determination)**:
  - If $\text{remainingDeficit} \le 0 \implies \text{GoalFeasibility.ACHIEVED}$
  - Else if $\text{safeContributionCapacity} \ge \text{requiredMonthlyContribution} \implies \text{GoalFeasibility.ON\_TRACK}$
  - Else if $\text{safeContributionCapacity} > 0 \implies \text{GoalFeasibility.AT\_RISK}$
  - Else ($\text{safeContributionCapacity} \le 0$) $\implies \text{GoalFeasibility.UNACHIEVABLE}$
- **`REQ-GOAL-010` (Projected Completion Date)**: When $\text{safeContributionCapacity} > 0$ and $\text{remainingDeficit} > 0$:
  $$\text{projectedMonths} = \lceil \text{remainingDeficit} / \text{safeContributionCapacity} \rceil$$
  $$\text{projectedCompletionDate} = \text{evaluationDate} + \text{projectedMonths}$$
  If $\text{safeContributionCapacity} == 0$, `projectedCompletionDate` SHALL be null.

### 5.4 Multi-Goal Waterfall Strategy & Simulation
- **`REQ-GOAL-011` (Multi-Goal Waterfall Evaluation)**: When evaluating multiple goals for a wallet, total `safeContributionCapacity` SHALL be allocated in descending priority order (`CRITICAL` $\rightarrow$ `HIGH` $\rightarrow$ `MEDIUM` $\rightarrow$ `LOW`).
- **`REQ-GOAL-012` (Stateless Simulation Endpoint)**: The system SHALL expose `POST /goals/simulate` to simulate hypothetical goals against input cashflow profiles without writing to the database.

---

## 6. Non-Functional Requirements

- **Performance**: In-memory strategy calculations SHALL execute in $< 1\text{ms}$ ($O(N)$ for $N \le 50$ goals).
- **Modulith Architecture Boundary**:
  - `br.com.wallet.goals` is an independent Spring Modulith module.
  - Allowed dependencies: `core::api`, `ledger::api` (for querying balances via `BalanceUseCase`).
  - Zero imports from `br.com.wallet.ledger.internal.*` or foreign DAOs.
- **Test Coverage**: $\ge 90\%$ line coverage across `goals` engine and services.

---

## 7. Module & Package Topology

```text
br.com.wallet.goals
│
├── package-info.java                   (@ApplicationModule(allowedDependencies = {"ledger::api", "ledger", "core::api", "core"}))
│
├── api                                 (Published Public Interface)
│   ├── FinancialGoalUseCase.java       (Lifecycle: create, update, pause, resume, cancel, markAchieved)
│   ├── CashflowProfileUseCase.java     (Cashflow profile: save, get)
│   ├── GoalStrategyUseCase.java        (Strategy & Simulation: calculateStrategy, simulate, evaluateWallet)
│   ├── GoalQueryUseCase.java           (Queries: getGoal, listGoals)
│   │
│   ├── model/                          (Domain Records & Enums)
│   │   ├── FinancialGoal.java
│   │   ├── CashflowProfile.java
│   │   ├── GoalStrategy.java
│   │   ├── MultiGoalStrategyReport.java
│   │   ├── GoalPriority.java           (CRITICAL, HIGH, MEDIUM, LOW)
│   │   ├── GoalStatus.java             (ACTIVE, PAUSED, ACHIEVED, CANCELLED)
│   │   └── GoalFeasibility.java        (ON_TRACK, AT_RISK, UNACHIEVABLE, ACHIEVED)
│   │
│   └── dto/                            (Commands & Payloads)
│       ├── CreateGoalCommand.java
│       ├── UpdateGoalCommand.java
│       ├── SaveCashflowProfileCommand.java
│       ├── SimulateGoalCommand.java
│       ├── GoalResponse.java
│       └── GoalStrategyResponse.java
│
└── internal                            (Protected Implementation Packages)
    ├── engine/                         (Pure In-Memory Calculators)
    │   ├── ContributionCalculator.java
    │   ├── CashflowCapacityCalculator.java
    │   └── GoalStrategyEngine.java
    │
    ├── service/                        (Application Services)
    │   ├── FinancialGoalService.java
    │   ├── CashflowProfileService.java
    │   ├── GoalStrategyService.java
    │   └── GoalQueryService.java
    │
    └── persistence/                    (Spring JDBC DAOs)
        ├── FinancialGoalDao.java
        └── CashflowProfileDao.java
```

---

## 8. Database Schema (`docker/init/schema.sql`)

```sql
-- =========================================================================
-- Financial Goals Capability Module Tables (SPEC-002 / PLAN-002)
-- =========================================================================

CREATE TABLE IF NOT EXISTS goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL REFERENCES accounts(id),
    target_wallet_id UUID NULL REFERENCES accounts(id),
    name VARCHAR(128) NOT NULL,
    target_amount NUMERIC(19, 2) NOT NULL CHECK (target_amount > 0),
    target_date DATE NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_goal_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_goal_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ACHIEVED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_goals_user ON goals(user_id);
CREATE INDEX IF NOT EXISTS idx_goals_wallet_status ON goals(wallet_id, status);

CREATE TABLE IF NOT EXISTS cashflow_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    monthly_income NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (monthly_income >= 0),
    monthly_committed_expenses NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (monthly_committed_expenses >= 0),
    minimum_safety_buffer NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (minimum_safety_buffer >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cashflow_wallet ON cashflow_profiles(wallet_id);
```

---

## 9. REST API Contract (`br.com.wallet.infrastructure.rest.controller.GoalController`)

```http
### 1. Create a Financial Goal
POST /goals
Headers:
  Content-Type: application/json
Body:
{
  "userId": "a1111111-1111-1111-1111-111111111111",
  "walletId": "0a35fb14-75ee-4125-943b-500893c30d33",
  "name": "House Downpayment 2028",
  "targetAmount": 100000.00,
  "targetDate": "2028-12-31",
  "priority": "HIGH"
}
Response: 201 Created (GoalResponse)

### 2. Save / Update Cashflow Profile
PUT /goals/cashflow
Body:
{
  "userId": "a1111111-1111-1111-1111-111111111111",
  "walletId": "0a35fb14-75ee-4125-943b-500893c30d33",
  "monthlyIncome": 12000.00,
  "monthlyCommittedExpenses": 6000.00,
  "minimumSafetyBuffer": 2500.00
}
Response: 200 OK (CashflowProfile)

### 3. Get Strategy Recommendation for a Goal
GET /goals/{id}/strategy
Response: 200 OK
{
  "goalId": "...",
  "targetAmount": 100000.00,
  "currentAccumulatedAmount": 20000.00,
  "remainingDeficit": 80000.00,
  "remainingMonths": 28,
  "requiredMonthlyContribution": 2857.14,
  "safeMonthlyContributionCapacity": 3500.00,
  "recommendedMonthlyContribution": 2857.14,
  "feasibility": "ON_TRACK",
  "projectedCompletionDate": "2028-10-31",
  "evaluationDate": "2026-08-27"
}

### 4. Get Multi-Goal Strategy Analysis Report for Wallet
GET /goals/wallet/{walletId}/strategy-report
Response: 200 OK (MultiGoalStrategyReport)

### 5. Stateless Simulation (No DB Write)
POST /goals/simulate
Body:
{
  "targetAmount": 100000.00,
  "targetDate": "2028-12-31",
  "currentBalance": 20000.00,
  "cashflowProfile": {
    "monthlyIncome": 12000.00,
    "monthlyCommittedExpenses": 6000.00,
    "minimumSafetyBuffer": 2500.00
  }
}
Response: 200 OK (GoalStrategyResponse)
```

---

## 10. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant Enforced |
| :--- | :--- | :--- |
| `targetDate` in the past or today | Validation error `400 Bad Request` | `REQ-GOAL-001` |
| `monthlyCommittedExpenses + minimumSafetyBuffer` $\ge$ `monthlyIncome` | `safeContributionCapacity = 0.00`, feasibility is `UNACHIEVABLE` | `I-GOAL-002` |
| Multi-goal priority conflict | Waterfall algorithm allocates capacity to `CRITICAL` and `HIGH` before `MEDIUM` and `LOW` | `I-GOAL-006` |
| Attempting direct database write to `accounts` or `ledger` from `goals` | Architecture test failure | `I-GOAL-001`, `I-MODULITH-001` |

---

## 11. Acceptance Criteria

- [ ] `br.com.wallet.goals` application module created with Modulith `@ApplicationModule` configuration.
- [ ] `goals` and `cashflow_profiles` tables created in schema with check constraints and indexes.
- [ ] Pure deterministic strategy engine (`GoalStrategyEngine`, `ContributionCalculator`, `CashflowCapacityCalculator`) implemented with explicit `evaluationDate` and `HALF_EVEN` rounding.
- [ ] Feasibility classifications (`ON_TRACK`, `AT_RISK`, `UNACHIEVABLE`, `ACHIEVED`) verified across parameterized unit tests.
- [ ] Multi-goal waterfall priority evaluation verified with unit tests.
- [ ] Stateless simulation (`POST /goals/simulate`) and live strategy queries exposed via REST API.
- [ ] `ModulithArchitectureTest.verifyArchitecture()` passes with 0 violations and 0 cycles.
- [ ] JaCoCo test coverage exceeds $\ge 90\%$ for `goals` domain and strategy engine.
