# 📋 Specification: SPEC-003.1 — Recurring Pattern & Subscription Detection

- **Status**: Ratified
- **Author**: Antigravity Intelligence & Financial Planning Team
- **Date**: 2026-10-07
- **Target Release / Milestone**: Wallet Service V4 — Phase 3.1
- **Bounded Context / Module**: `br.com.wallet.intelligence`
- **Spec Slicing Scope**: Atomic Slice ($\le 250$ lines, `I-SDD-006`)

---

## 0. Pre-Flight History & Context Audit

- **Histories & Summaries Audited**:
  - `.histories/history0.txt`: Subscription detection concept, Visa-style alert triggers.
  - `.histories/history1.txt`: Deterministic feature extraction engine without LLM dependencies.
  - `.histories/history84.txt`: Hybrid architecture: deterministic pattern engine (`[MUST]`) + additive semantic classification via Native Typed Decision Algebra (`SPEC-000.8.1`) and ONNX micro-ML (`SPEC-000.8`).
  - `.histories/history85.txt` & `history86.txt`: Decoupled capability slice: mathematical recurrence detection separate from semantic enrichment, failure isolation, temporal truth, `eventId` vs `operationId` idempotency, and precision separation.
  - `.spec/SPEC-003-spending-and-subscription-intelligence.md`: Module foundation and invariants.
- **Foundational Constraints (`constitution.md` & `SPEC-003`)**:
  - `I-INTEL-001`: Zero direct ledger mutation.
  - `I-INTEL-002`: In-process Modulith event ingestion via `event_publication` (zero NATS boomerang).
  - `I-INTEL-003`: Precision policy (scale 2 monetary, $\ge 6$ decimal places statistical).
  - `I-INTEL-004`: Strict API boundary against ledger internals (`ledger::api` only).
  - `I-INTEL-007`: Semantic evaluator failure isolation.
  - `I-INTEL-008`: Idempotent event processing under Modulith at-least-once redelivery (`eventId` + `operationId`).
  - `I-INTEL-009`: Temporal truth / no future leakage.
  - `I-INTEL-010`: Strict tenant isolation (`tenant_id VARCHAR(64) NOT NULL`).
  - `I-TYPED-001` & `I-TYPED-006`: Hot-path airgap and anti-coercion semantics for typed decisions.

---

## 1. Intent & Business Value

Financial wallet users struggle to monitor recurring subscription costs and hidden service price increases. This capability provides autonomous in-process detection of recurring outflows, periodicity categorization, price spike alerting, and additive semantic classification (e.g. `SUBSCRIPTION` vs `INSTALLMENT` vs `UTILITY`) with strict multi-tenant isolation.

---

## 2. Scope & Non-Goals

### In Scope
- Asynchronous capture of debit events (`TransferCompletedEvent`, `WithdrawCompletedEvent`).
- Transaction series clustering strictly by `(tenantId, walletId, counterpartyId)`.
- Cadence classification (`WEEKLY`, `BI_WEEKLY`, `MONTHLY`, `ANNUAL`) and amount variance tracking.
- Three-stage promotion lifecycle (`DISCOVERED` $\to$ `CANDIDATE` $\to$ `ACTIVE`).
- Price spike detection ($\ge +5\%$) and lapsed cancellation inference.
- Schema ownership for `subscriptions` table in `docker/init/schema.sql` with multi-tenant unique constraints.
- Additive semantic classification via `DecisionQuestion<SubscriptionClassification>` (`SPEC-000.8.1`).
- Query REST API: `GET /api/v1/intelligence/subscriptions/{walletId}` scoped to tenant.

### Non-Goals
- Cashflow forecasting calendar & Goals synchronization (deferred to `SPEC-003.2`).
- Automatic transaction cancellation or payment blocking (deferred to Phase 4 AI Copilot).
- External banking transaction ingestion (out-of-scope for internal ledger wallet).

---

## 3. Mathematical & System Invariants

- **`I-SUB-001` (Cadence Interval & Periodicity Classification)**:
  Let $\Delta t_i = t_i - t_{i-1}$ be the elapsed time in days between consecutive observed debit transactions for series $(T, W, C)$. The average interval $\overline{\Delta t}$ classifies cadence deterministically:
  $$\text{Cadence} = \begin{cases} 
  \text{WEEKLY} & \text{if } |\overline{\Delta t} - 7| \le 1 \\
  \text{BI\_WEEKLY} & \text{if } |\overline{\Delta t} - 14| \le 2 \\
  \text{MONTHLY} & \text{if } |\overline{\Delta t} - 30| \le 3 \\
  \text{ANNUAL} & \text{if } |\overline{\Delta t} - 365| \le 5 \\
  \text{IRREGULAR} & \text{otherwise}
  \end{cases}$$
- **`I-SUB-002` (Confidence Score & Activation Gate)**:
  Given cycle count $N$ and amount coefficient of variation $CV_A = \frac{\sigma_A}{\mu_A}$:
  $$\text{Confidence}(N, CV_A) = \min\left(1.00, \, \frac{N}{3} \times (1.00 - \min(0.50, CV_A))\right)$$
  A subscription transitions to `ACTIVE` if and only if $N \ge 3$ and $\text{Confidence} \ge 0.70$.
- **`I-SUB-003` (Price Spike Invariant)**:
  For an active subscription with baseline mean $\mu_A$, if observed amount $A_{\text{new}} \ge \mu_A \times 1.05$ (jump $\ge +5.00\%$), state transitions to `PRICE_SPIKE_DETECTED` and emits `SubscriptionPriceSpikeEvent`.
- **`I-SUB-004` (Cancellation Inference)**:
  If elapsed time since last transaction exceeds $1.50 \times \overline{\Delta t}$, status transitions to `CANCELLED_INFERRED`.
- **`I-SUB-005` (Graceful Semantic Degradation & Anti-Coercion)**:
  Semantic classification via `DecisionQuestion<SubscriptionClassification>` MUST gracefully return `DecisionUnavailable<T>` if an optional evaluator fails or times out (`I-TYPED-005`). `DecisionComposer` MUST NOT coerce unavailable outcomes to synthetic defaults (`I-TYPED-006`). Inconclusive evaluation yields `DecisionAnswer(UNKNOWN)`.
- **`I-SUB-006` (Decoupled Semantic Authority)**:
  AI/ML models and heuristic rule evaluators SHALL NEVER dictate the `ACTIVE` lifecycle status. The lifecycle is strictly governed by deterministic mathematical invariants (`I-SUB-001`, `I-SUB-002`).
- **`I-SUB-007` (Precision Separation Invariant)**:
  Monetary values (`averageAmount`, `lastAmount`) MUST use canonical `BigDecimal` scale 2 (`HALF_EVEN`). Statistical variables ($\sigma_A, CV_A, \text{Confidence}$) MUST maintain $\ge 6$ decimal places intermediate precision (`I-INTEL-003`).
- **`I-SUB-008` (Idempotent Event Ingestion)**:
  Handling of `TransferCompletedEvent` and `WithdrawCompletedEvent` MUST guarantee idempotent side effects based on canonical `eventId` and financial `operationId`. Modulith redeliveries SHALL NOT duplicate cycle counts $N$ or emit duplicate alerts (`I-INTEL-008`).
- **`I-SUB-009` (Temporal Truth & Failure Isolation)**:
  Observation at timestamp $t$ MUST use only transactions $\le t$ (`I-INTEL-009`). Evaluator failures MUST NOT prevent candidate persistence or Core transactions (`I-INTEL-007`).
- **`I-SUB-010` (Strict Tenant Partitioning Invariant)**:
  Every detected subscription is uniquely bound to $(T, W, C)$ with `tenant_id VARCHAR(64) NOT NULL` (`I-INTEL-010`). Transactions belonging to tenant $T_A$ SHALL NEVER contribute to series, cadences, or confidence metrics of tenant $T_B$.

---

## 4. Functional Requirements (MoSCoW Prioritized — `I-SDD-004`)

### 4.1 Must Have (`[MUST]`)
- **`REQ-SUB-001 [MUST]`**: Ingest `TransferCompletedEvent` and `WithdrawCompletedEvent` via `@ApplicationModuleListener`, filtering for `origin == OperationOrigin.USER`.
- **`REQ-SUB-002 [MUST]`**: Cluster transaction series deterministically by `(tenantId, walletId, counterpartyId)` (`I-SUB-010`).
- **`REQ-SUB-003 [MUST]`**: Enforce lifecycle states: $N=1 \implies \text{DISCOVERED}$, $N=2 \implies \text{CANDIDATE}$, $N \ge 3 \land \text{Confidence} \ge 0.70 \implies \text{ACTIVE}$ (`I-SUB-002`).
- **`REQ-SUB-004 [MUST]`**: Calculate average amount, cadence (`I-SUB-001`), and projected next renewal date.
- **`REQ-SUB-005 [MUST]`**: Classify subscription amount variance: `FIXED` (if $CV_A \le 0.05$) or `VARIABLE` (if $CV_A > 0.05$).
- **`REQ-SUB-006 [MUST]`**: Detect price increases $\ge 5\%$ (`I-SUB-003`), setting status to `PRICE_SPIKE_DETECTED` and emitting `SubscriptionPriceSpikeEvent`.
- **`REQ-SUB-007 [MUST]`**: Provide schema DDL migration for `subscriptions` table in `docker/init/schema.sql` with `tenant_id VARCHAR(64) NOT NULL`, composite index `(tenant_id, wallet_id)`, and `UNIQUE (tenant_id, wallet_id, counterparty_id)`.
- **`REQ-SUB-008 [MUST]`**: Expose `GET /api/v1/intelligence/subscriptions/{walletId}` scoped to tenant with optional `?status=` filter.

### 4.2 Should Have (`[SHOULD]`)
- **`REQ-SUB-009 [SHOULD]`**: Transition active subscriptions to `CANCELLED_INFERRED` when elapsed time exceeds $1.50 \times \overline{\Delta t}$ (`I-SUB-004`).
- **`REQ-SUB-010 [SHOULD]`**: Asynchronously evaluate `DecisionQuestion<SubscriptionClassification>` over candidate subscriptions using `SPEC-000.8.1` Typed Decision Algebra.
- **`REQ-SUB-011 [SHOULD]`**: Support ONNX Micro-ML evaluator (`SPEC-000.8`) for local, fast subscription likelihood scoring.

### 4.3 Could Have (`[COULD]`)
- **`REQ-SUB-012 [COULD]`**: Nearline Ollama SLM evaluator for counterparty descriptor disambiguation.

### 4.4 Won't Have (`[WON'T]`)
- **`REQ-SUB-013 [WON'T]`**: Model authority over financial state or `ACTIVE` lifecycle gates (`I-SUB-006`).
- **`REQ-SUB-014 [WON'T]`**: Direct ledger mutations or automated balance withdrawals (`I-INTEL-001`).

---

## 5. Interface Contracts

### REST API
```http
GET /api/v1/intelligence/subscriptions/{walletId}?status=ACTIVE
Response (200 OK):
[
  {
    "id": "s1000000-0000-0000-0000-000000000001",
    "tenantId": "tenant-alpha",
    "walletId": "w1000000-0000-0000-0000-000000000001",
    "counterpartyId": "c1000000-0000-0000-0000-000000000001",
    "cadence": "MONTHLY",
    "status": "ACTIVE",
    "classification": "SUBSCRIPTION",
    "averageAmount": 39.90,
    "lastAmount": 39.90,
    "confidence": 0.95,
    "observedCycles": 4,
    "nextExpectedAt": "2026-11-01T12:00:00Z"
  }
]
```

### Typed Decision Classification Enum
```java
public enum SubscriptionClassification implements DecisionValue {
    SUBSCRIPTION, INSTALLMENT, UTILITY, INSURANCE, MEMBERSHIP, DONATION, UNKNOWN
}
```

---

## 6. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Boundary / Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-SUB-003` / `004` | 3 monthly transfers $\to$ `ACTIVE` with `MONTHLY` cadence | Non-USER event $\to$ 0 subscriptions created | Cycle count $< 3 \to$ status remains `CANDIDATE` |
| `REQ-SUB-002` / `007` | Series clustered by $(T, W, C)$ | Missing tenant $\to$ `TenantContextMissingException` | Events from Tenant B $\to$ zero mutation to Tenant A series (`I-SUB-010`) |
| `REQ-SUB-005` | Constant R$ 39.90 $\to$ `FIXED` | Irregular amounts $\to$ `VARIABLE` | Negative / zero amounts rejected |
| `REQ-SUB-006` | R$ 39.90 jump to R$ 45.90 (+15%) $\to$ `PRICE_SPIKE_DETECTED` | Price jump $< 5\% \to$ remains `ACTIVE` | Negative price ratio rejected |
| `REQ-SUB-010` | Evaluator classifies SaaS merchant $\to$ `SUBSCRIPTION` | Evaluator timeout $\to$ `DecisionUnavailable` without failing candidate | Coercing unavailable outcome to default $\to$ rejected (`I-TYPED-006`) |

---

## 7. Acceptance Criteria (`I-SDD-002`)

- [ ] All `[MUST]` requirements passing with unit and integration tests (`SubscriptionDetectionIT`).
- [ ] Schema migration verified in `schema.sql` (`subscriptions` table with `tenant_id` and unique index).
- [ ] Cross-tenant isolation verified with zero series contamination (`TenantIsolationIT`).
- [ ] Modulith architecture test passes with 0 cycle violations.
- [ ] Zero Spec-Drift: Implementation matches specification 100% (`I-SDD-003`).
