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
  - `.histories/history0.txt` & `history1.txt`: Subscription detection concept and deterministic feature extraction.
  - `.histories/history84.txt` to `history88.txt`: 3-pillar decoupling: Foundation (003.0), Pattern Engine (003.1), Cashflow Forecast (003.2), and Typed Decision Algebra (`SPEC-000.8.1`).
  - `.histories/history89.txt`: Mathematical contract closure, outflow observation definition (`I-SUB-011`), separation of lifecycle status (`ACTIVE`) from price condition (`PRICE_SPIKE_DETECTED`), population standard deviation, pre-observation price baseline, and authenticated tenant scoping.
- **Foundational Constraints (`constitution.md` & `SPEC-003`)**:
  - `I-INTEL-001`: Zero direct ledger mutation.
  - `I-INTEL-002`: In-process Modulith event ingestion via `event_publication` (zero NATS boomerang).
  - `I-INTEL-003`: Precision policy (scale 2 monetary, $\ge 6$ decimal places statistical).
  - `I-INTEL-004`: Strict API boundary against ledger internals (`ledger::api` only).
  - `I-INTEL-008`: Idempotent event processing under Modulith redelivery (`eventId` + `operationId`).
  - `I-INTEL-010`: Strict tenant isolation (`tenant_id VARCHAR(64) NOT NULL`).

---

## 1. Intent & Business Value

Financial wallet users struggle to monitor recurring subscription commitments and hidden price increases. This capability provides autonomous in-process detection of recurring outflows, cadence categorization, historical price spike alerting, and additive semantic classification with strict multi-tenant isolation.

---

## 2. Scope & Non-Goals

### In Scope
- Asynchronous capture of debit outflows (`TransferCompletedEvent` where `origin == USER`).
- Transaction series clustering strictly by `(tenantId, walletId, counterpartyId)`.
- Cadence classification (`WEEKLY`, `BI_WEEKLY`, `MONTHLY`, `ANNUAL`, `IRREGULAR`) and next expected renewal calculation.
- Lifecycle state machine: `DISCOVERED` ($N=1$), `CANDIDATE` ($N=2$), `ACTIVE` ($N \ge 3 \land \text{Confidence} \ge 0.70$), and `CANCELLED_INFERRED`.
- Independent price condition tracking (`PriceState`: `NORMAL`, `PRICE_SPIKE_DETECTED`) with `SubscriptionPriceSpikeEvent`.
- Schema ownership for `subscriptions` table in `docker/init/schema.sql` with multi-tenant unique constraints.
- Integration seam with `SPEC-000.8.1` `DecisionQuestion<SubscriptionClassification>`.
- Query REST API: `GET /api/v1/intelligence/subscriptions/{walletId}` with authenticated tenant context.

### Non-Goals
- Cashflow forecasting calendar & Goals synchronization (deferred to `SPEC-003.2`).
- Concrete AI/SLM/ONNX evaluator implementations (owned by `SPEC-000.8.1`).
- Automatic transaction cancellation or payment blocking.

---

## 3. Mathematical & System Invariants

- **`I-SUB-001` (Cadence Interval & Periodicity Classification)**:
  Let $\Delta t_i = t_i - t_{i-1}$ be the elapsed time in days between consecutive observed debit transactions for series $(T, W, C)$. Average interval $\overline{\Delta t}$ classifies cadence deterministically:
  $$\text{Cadence} = \begin{cases} 
  \text{WEEKLY} & \text{if } |\overline{\Delta t} - 7| \le 1 \\
  \text{BI\_WEEKLY} & \text{if } |\overline{\Delta t} - 14| \le 2 \\
  \text{MONTHLY} & \text{if } |\overline{\Delta t} - 30| \le 3 \\
  \text{ANNUAL} & \text{if } |\overline{\Delta t} - 365| \le 5 \\
  \text{IRREGULAR} & \text{otherwise}
  \end{cases}$$
  Projected renewal: $\text{nextExpectedAt} = t_{\text{last}} + \text{round}(\overline{\Delta t})$ days (in UTC `Instant`). If `Cadence == IRREGULAR`, $\text{nextExpectedAt} = \text{null}$.
- **`I-SUB-002` (Confidence Score & Population Statistics)**:
  For $N$ qualifying debit observations in series $(T, W, C)$ ($\le t$):
  $$\mu_A = \frac{1}{N}\sum_{i=1}^N A_i, \quad \sigma_A = \sqrt{\frac{1}{N}\sum_{i=1}^N (A_i - \mu_A)^2}, \quad CV_A = \frac{\sigma_A}{\mu_A}$$
  $$\text{Confidence}(N, CV_A) = \min\left(1.00, \, \frac{N}{3} \times (1.00 - \min(0.50, CV_A))\right)$$
  Subscription transitions to `ACTIVE` iff $N \ge 3$ and $\text{Confidence} \ge 0.70$.
- **`I-SUB-003` (Price Spike Baseline & Alert Invariant)**:
  For an observed amount $A_{\text{new}}$ at timestamp $t$, the baseline mean $\mu_A(t)$ MUST be calculated exclusively from qualifying observations strictly preceding $t$ ($A_{\text{new}}$ does not participate in its own baseline). If $A_{\text{new}} \ge \mu_A(t) \times 1.05$ (jump $\ge +5.00\%$), `priceState` transitions to `PRICE_SPIKE_DETECTED` and emits `SubscriptionPriceSpikeEvent`. Subscription status remains `ACTIVE`.
- **`I-SUB-004` (Cancellation Inference & Reactivation)**:
  For an `ACTIVE` subscription with non-`IRREGULAR` cadence, if elapsed time $t - t_{\text{last}} > 1.50 \times \overline{\Delta t}$, status transitions to `CANCELLED_INFERRED`. If a new matching observation subsequently arrives, status transitions to `CANDIDATE`.
- **`I-SUB-005` (Degradation vs. UNKNOWN Distinction)**:
  Evaluator timeout or infrastructure failure returns `DecisionUnavailable<T>` (`I-TYPED-005`). A successfully executed but inconclusive evaluation returns `DecisionAnswer(UNKNOWN)`. Coercing `DecisionUnavailable` to default is strictly forbidden (`I-TYPED-006`).
- **`I-SUB-006` (Decoupled Semantic Authority)**:
  AI/ML models and heuristic evaluators SHALL NEVER dictate the `ACTIVE` lifecycle status, which is strictly governed by deterministic mathematical gates (`I-SUB-001`, `I-SUB-002`).
- **`I-SUB-007` (Precision Separation Invariant)**:
  Monetary values (`averageAmount`, `lastAmount`) MUST use canonical `BigDecimal` scale 2 (`HALF_EVEN`). Statistical metrics ($\sigma_A, CV_A, \text{Confidence}$) use $\ge 6$ decimal places.
- **`I-SUB-008` (Idempotent Event Ingestion)**:
  Side effects MUST be idempotent based on canonical `eventId` and `operationId` (`I-INTEL-008`). Redeliveries SHALL NOT duplicate observation count $N$ or emit duplicate alerts.
- **`I-SUB-009` (Temporal Truth & Failure Isolation)**:
  Observations at timestamp $t$ MUST use only data $\le t$. Evaluator failures MUST NOT prevent candidate persistence or Core transactions (`I-INTEL-007`).
- **`I-SUB-010` (Strict Tenant Partitioning Invariant)**:
  Every detected subscription is uniquely bound to $(T, W, C)$ with `tenant_id VARCHAR(64) NOT NULL`. The query API MUST resolve tenant scope from the authenticated security context, and MUST NOT accept `tenantId` as a client-controlled request selector.
- **`I-SUB-011` (Outflow Observation Contract)**:
  Only completed USER-originated outflows (`origin == OperationOrigin.USER`) participate in subscription series. For `TransferCompletedEvent`, `walletId = from` (debit) and `counterpartyId = to` (destination). Credits, inbound transfers, non-USER events, or events without stable counterparty identity MUST NOT enter a subscription series.

---

## 4. Functional Requirements (MoSCoW Prioritized — `I-SDD-004`)

### 4.1 Must Have (`[MUST]`)
- **`REQ-SUB-001 [MUST]`**: Ingest `TransferCompletedEvent` via `@ApplicationModuleListener`, filtering for `origin == OperationOrigin.USER` (`I-SUB-011`).
- **`REQ-SUB-002 [MUST]`**: Cluster transaction series deterministically by `(tenantId, walletId, counterpartyId)` (`I-SUB-010`).
- **`REQ-SUB-003 [MUST]`**: Enforce lifecycle states: $N=1 \implies \text{DISCOVERED}$, $N=2 \implies \text{CANDIDATE}$, $N \ge 3 \land \text{Confidence} \ge 0.70 \implies \text{ACTIVE}$ (`I-SUB-002`).
- **`REQ-SUB-004 [MUST]`**: Calculate cadence (`I-SUB-001`), population $\sigma_A$, and projected $\text{nextExpectedAt}$ (null if `IRREGULAR`).
- **`REQ-SUB-005 [MUST]`**: Classify amount variance: `FIXED` (if $CV_A \le 0.05$) or `VARIABLE` (if $CV_A > 0.05$).
- **`REQ-SUB-006 [MUST]`**: Detect price increases $\ge 5\%$ against historical baseline $\mu_A(t)$ (`I-SUB-003`), setting `priceState = PRICE_SPIKE_DETECTED` and emitting `SubscriptionPriceSpikeEvent`.
- **`REQ-SUB-007 [MUST]`**: Provide schema DDL migration for `subscriptions` table in `docker/init/schema.sql` with `tenant_id VARCHAR(64) NOT NULL`, `price_state VARCHAR(32) NOT NULL DEFAULT 'NORMAL'`, composite index `(tenant_id, wallet_id)`, and `UNIQUE (tenant_id, wallet_id, counterparty_id)`.
- **`REQ-SUB-008 [MUST]`**: Expose `GET /api/v1/intelligence/subscriptions/{walletId}` scoped strictly to authenticated tenant context with optional `?status=` filter.

### 4.2 Should Have (`[SHOULD]`)
- **`REQ-SUB-009 [SHOULD]`**: Transition active subscriptions to `CANCELLED_INFERRED` when elapsed time exceeds $1.50 \times \overline{\Delta t}$, and reactivate to `CANDIDATE` upon new observation (`I-SUB-004`).
- **`REQ-SUB-010 [SHOULD]`**: Expose subscription classification context through `DecisionQuestion<SubscriptionClassification>` contract (`SPEC-000.8.1`). Concrete evaluators are owned by `SPEC-000.8.1`.
- **`REQ-SUB-011 [SHOULD]`**: Support nearline semantic classification when compatible `SPEC-000.8.1` evaluator is available, without hard runtime dependency on ONNX or Ollama (`I-SUB-006`).

### 4.3 Could Have (`[COULD]`)
- **`REQ-SUB-012 [COULD]`**: Counterparty descriptor normalization seam for merchant aliases.

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
    "id": "f1000000-0000-0000-0000-000000000001",
    "tenantId": "tenant-alpha",
    "walletId": "a1000000-0000-0000-0000-000000000001",
    "counterpartyId": "c1000000-0000-0000-0000-000000000001",
    "cadence": "MONTHLY",
    "status": "ACTIVE",
    "priceState": "NORMAL",
    "classification": "SUBSCRIPTION",
    "averageAmount": 39.90,
    "lastAmount": 39.90,
    "confidence": 0.950000,
    "observedCycles": 4,
    "nextExpectedAt": "2026-11-01T12:00:00Z"
  }
]
```

---

## 6. Mandatory Test Triad (`I-TDD-002`)

| Requirement | 1. Positive Canonical Test | 2. Boundary / Invalid Input Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-SUB-003` / `004` | 3 monthly transfers ($CV_A \le 0.30$) $\to$ `ACTIVE`, `MONTHLY` | Non-USER event $\to$ 0 subscriptions created (`I-SUB-011`) | $N=3, CV_A > 0.30 \implies \text{Confidence} < 0.70 \implies \text{CANDIDATE}$ |
| `REQ-SUB-002` / `007` | Series clustered by $(T, W, C)$ | Unauthenticated tenant $\to$ rejected | Tenant B events do not modify Tenant A series (`I-SUB-010`) |
| `REQ-SUB-005` | Constant R$ 39.90 ($CV_A = 0$) $\to$ `FIXED`, $\text{Confidence} = 1.00$ | Irregular amounts ($CV_A > 0.05$) $\to$ `VARIABLE` | Negative / zero amounts rejected |
| `REQ-SUB-006` | R$ 39.90 jump to R$ 45.90 (+15% vs baseline) $\to$ `PRICE_SPIKE_DETECTED` | Price jump $< 5\% \to$ remains `NORMAL` | $A_{\text{new}}$ included in baseline $\to$ assertion fails (`I-SUB-003`) |
| `REQ-SUB-010` | Evaluator classifies SaaS $\to$ `SUBSCRIPTION` | Evaluator timeout $\to$ `DecisionUnavailable` without failing candidate | Coercing unavailable outcome to default $\to$ rejected (`I-TYPED-006`) |
