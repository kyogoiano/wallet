# 📋 Specification: SPEC-000.6 — Fraud Risk Propagation & Temporal Decay Engine (Histories 12, 14, 15, 17)

- **Status**: Reviewed & Ratified
- **Author**: Antigravity Financial & Risk Engineering Team
- **Date**: 2026-08-30
- **Source Reference**: [`.histories/history12.txt`](file:///.histories/history12.txt), [`.histories/history14.txt`](file:///.histories/history14.txt), [`.histories/history15.txt`](file:///.histories/history15.txt), [`.histories/history17.txt`](file:///.histories/history17.txt)
- **Target Release / Milestone**: Wallet Service V4.x — Fraud Intelligence Evolution (Phase 0.6)
- **Architectural Mantra**: *"Risk is contagious, but decays exponentially along network paths: calculate multi-path influence with temporal evidence from fraud_relationship_events and probabilistic union."*

---

## 1. Intent & Business Value

When a known fraudulent account or high-risk entity (e.g. `User A`) is blocked or flagged, existing systems only stop transactions directly involving `User A`. However, sophisticated fraud rings bypass simple blocklists by routing money through mule accounts (`User B`, `User C`) or sharing compromised infrastructure (devices, IP subnets, credit cards).

This specification builds the **Risk Propagation & Temporal Decay Engine**:
1. It calculates how risk spreads across connected nodes in the relational graph ($k$-hop depth) using **temporal evidence from `fraud_relationship_events`**.
2. It applies a **mathematically rigorous path influence decay model**:
   $$I(p, t) = R_{\text{source}}(v) \cdot \prod_{e \in p} w(e) \cdot \prod_{e \in p} e^{-\lambda \Delta t_e}$$
3. It resolves multi-path convergence without double-counting via **probabilistic union**:
   $$R_{\text{propagated}}(u) = 1 - \prod_{p \in \text{Paths}(u)} (1 - I(p, t))$$
4. It computes `propagated_risk` on `fraud_entities` with strict **risk dimension isolation (`I-PROP-005`)** without touching `direct_risk`, `graph_risk`, or `final_risk`.
5. It enforces **bounded traversal limits (`I-PROP-006`)** and model versioning (`propagation_model_version`).
6. It emits **`EntityRiskPropagationDetectedEvent`** for downstream fusion (`SPEC-000.8`) and intelligence alerts.

---

## 2. Scope & Non-Goals

### In Scope
- **`REQ-PROP-001` (Relationship Edge Weight Configuration)**:
  - `SHARED_DEVICE`: $0.90$
  - `SHARED_PHONE`: $0.85$
  - `SHARED_EMAIL`: $0.75$
  - `TRANSFERRED_TO`: $0.60$
  - `SHARED_IP`: $0.35$
- **`REQ-PROP-002` (Temporal Path Influence with Half-Life Parameterization)**:
  - $\lambda = \frac{\ln 2}{t_{1/2}}$, configured via `fraud.propagation.temporal-decay.half-life = 7d`.
  - For each edge $e = (x, y)$, $\Delta t_e = t - t_{\text{last\_event}}$, where $t_{\text{last\_event}}$ is obtained from `fraud_relationship_events`.
- **`REQ-PROP-003` (Multi-Path Probabilistic Aggregation)**:
  - Aggregate independent paths reaching target entity $u$:
    $$R_{\text{propagated}}(u) = 1 - \prod_{p \in \text{Paths}(u)} (1 - I(p, t))$$
- **`REQ-PROP-004` (Bounded Traversal Limits)**:
  - Enforce strict limits: `max-hops = 3`, `max-entities = 1000`, `max-paths = 5000`.
- **`REQ-PROP-005` (Risk Dimension Isolation & Model Versioning)**:
  - Persist `propagated_risk`, `propagation_model_version` (e.g. `"v1"`), and `propagation_evaluated_at` to `fraud_entities`.
- **`REQ-PROP-006` (Decoupled Propagation Alert Event)**:
  - Publish `EntityRiskPropagationDetectedEvent` to NATS JetStream topic `events.fraud.propagation` when $R_{\text{propagated}} \ge \theta_{\text{alert}}$ (e.g. $0.60$).

### Non-Goals
- Calculating `final_risk` or enacting quarantine decisions (delegated to Signal Fusion in `SPEC-000.8`).
- Real-time synchronous calculation on the transfer path (propagation runs asynchronously via background sweeps or reactive event consumers).
- Vector embeddings and semantic analysis (covered in `SPEC-000.7`).

---

## 3. Mathematical & Architectural Invariants

- **`I-PROP-001` (Bounded Risk Range)**: All path influences and propagated risk scores MUST strictly belong to the unit interval $[0.0, 1.0]$.
- **`I-PROP-002` (Monotonic Temporal Decay)**: As time elapsed along any edge $\Delta t \to \infty$, path influence MUST strictly approach zero ($I(p, t) \to 0$).
- **`I-PROP-003` (Multi-Path Sub-Linear Saturation)**: Combining multiple paths must never exceed 1.0, adhering to the probabilistic union formula:
  $$R_{\text{propagated}} = 1 - \prod_{i} (1 - I_i)$$
- **`I-PROP-004` (Asynchronous Execution Guarantee)**: Risk propagation sweeps MUST execute in background workers or reactive NATS listeners, never blocking active transfer use cases.
- **`I-PROP-005` (Risk Dimension Isolation)**: The propagation engine MUST read `direct_risk` and graph facts as inputs but MUST only write `propagated_risk`. It MUST NOT calculate or mutate `final_risk`.
- **`I-PROP-006` (Bounded Traversal Invariant)**: A propagation execution MUST enforce configured bounds on hops, visited entities, and paths to prevent CPU/memory exhaustion.

---

## 4. Requirements & Acceptance Criteria

### REQ-PROP-001: Path Influence Calculation
- **Given** source risk $R_{\text{source}}(A) = 1.0$, a 2-hop path $A \xrightarrow{SHARED\_DEVICE} B \xrightarrow{TRANSFERRED\_TO} C$.
- **When** both edges occurred 7 days ago ($t_{1/2} = 7\text{ days} \implies e^{-\lambda \Delta t} = 0.5$).
- **Then** the path influence is:
  $$I(A \to B \to C) = 1.0 \times (0.90 \times 0.5) \times (0.60 \times 0.5) = 0.45 \times 0.30 = 0.135 \pm 0.001$$

### REQ-PROP-002: Multi-Path Aggregation (Anti-Double-Counting)
- **Given** two paths reaching entity $D$: $I(p_1) = 0.50$ and $I(p_2) = 0.40$.
- **When** multi-path aggregation executes.
- **Then** $R_{\text{propagated}}(D) = 1 - (1 - 0.50)(1 - 0.40) = 1 - (0.50 \times 0.60) = 0.70$.

### REQ-PROP-003: Model Versioning & Persistence
- **Given** a completed propagation sweep.
- **When** updating `fraud_entities`.
- **Then** it updates `propagated_risk`, `propagation_model_version = 'v1'`, `propagation_evaluated_at = now()` without modifying `direct_risk` or `graph_risk`.

### REQ-PROP-004: Event Notification
- **Given** an entity with calculated $R_{\text{propagated}} = 0.78 \ge \theta_{\text{alert}} = 0.60$.
- **When** propagation finishes.
- **Then** it publishes `EntityRiskPropagationDetectedEvent` to NATS with entity ID, score, hop count, and strongest relationship.
