# 📋 Specification: SPEC-000.7 — Fraud Behavioral Embeddings, Archetype Matching & Local LLM Dossier Synthesizer (Histories 12, 13, 14, 15, 18, 19, 20)

- **Status**: Reviewed & Ratified
- **Author**: Antigravity Financial & Risk Engineering Team
- **Date**: 2026-08-30
- **Source Reference**: [`.histories/history12.txt`](file:///.histories/history12.txt), [`.histories/history13.txt`](file:///.histories/history13.txt), [`.histories/history14.txt`](file:///.histories/history14.txt), [`.histories/history15.txt`](file:///.histories/history15.txt), [`.histories/history18.txt`](file:///.histories/history18.txt), [`.histories/history19.txt`](file:///.histories/history19.txt), [`.histories/history20.txt`](file:///.histories/history20.txt)
- **Target Release / Milestone**: Wallet Service V4.x — Fraud Intelligence Evolution (Phase 0.7)
- **Architectural Mantra**: *"Leverage pgvector with inner product (<#>) on PostgreSQL for deterministic 16-D behavioral similarity and local quantized Llama 3.1 for automated investigative triage without external cloud egress or PII exposure."*

---

## 1. Intent & Business Value

Graph topology identifies *who is connected to whom*, while **Behavioral Vector Embeddings** identify *who acts like a fraudster*.

This specification introduces:
1. **Behavioral Profile Feature Extraction (16-D Bounded & $L_2$-Normalized)**: Extracting exactly 16 normalized transactional metrics across a 30-day sliding window into a unit vector ($\|\vec{v}\|_2 = 1.0$).
2. **PostgreSQL + `pgvector` Inner Product Indexing (`fraud_entity_features`)**: Storing compact feature embeddings using `vector(16)` with fast negative inner product (`vector_ip_ops` / `<#>`) HNSW indexing.
3. **Fraud Archetype Centroid Matching (`fraud_archetype_centroids`)**: Matching entity vectors against calibrated fraud archetypes (`MONEY_MULE_RAPID_DRAIN`, `SMURFING`, `ACCOUNT_TAKEOVER`), calculating `behavioral_risk = \max_i (\vec{u} \cdot \vec{c}_i \times w_i)`.
4. **Local GraphRAG Investigation Dossier Synthesizer**: Assembling multi-hop graph facts, temporal evidence, and vector metrics into a structured, explainable `FraudInvestigationDossier` synthesized by a local quantized **Llama 3.1 8B** instance (via vLLM or Ollama/llama.cpp) with zero cloud egress and strict PII masking.

```mermaid
flowchart TD
    subgraph Vectors ["16-D Behavioral Embedding Engine (History 20)"]
        Hist[30-Day Transaction History] --> FeatExt[16-D Min-Max Scaler & L2 Normalization]
        FeatExt --> DB_Feat[(pgvector: fraud_entity_features)]
        Centroids[(pgvector: fraud_archetype_centroids)] --> Matcher[Inner Product Query: behavioral_vector <#> centroid]
        DB_Feat --> Matcher
        Matcher --> Score[behavioral_risk = max(sim * w_i)]
    end

    subgraph Investigation ["Local GraphRAG Investigation Pipeline"]
        Score --> Trigger{Alert / Manual Review}
        Trigger --> GraphContext[Assemble 2-Hop Graph Facts (SPEC-000.5)]
        Trigger --> TempContext[Assemble Temporal Evidence (SPEC-000.6)]
        GraphContext & TempContext & Matcher --> Sanitizer[PII Masking & Tokenization: MASK_USER_X]
        Sanitizer --> LLM["Local Llama 3.1 8B (vLLM / Ollama with JSON Mode)"]
        LLM --> Dossier[Structured FraudInvestigationDossier]
    end
```

---

## 2. Scope & Non-Goals

### In Scope
- **`REQ-VEC-001` (pgvector Extension, Archetypes & Inner Product Schema)**:
  - `fraud_entity_features` storing $L_2$-normalized `vector(16)`.
  - `fraud_archetype_centroids` storing reference vectors for known fraud archetypes.
  - HNSW indexing using `vector_ip_ops` with tuned parameters `(m = 16, ef_construction = 64)`.
- **`REQ-VEC-002` (Exact 16-D Normalized Feature Vector Construction - History 20)**:
  - Deterministic 16-dimension extraction mapped to $[0.0, 1.0]$ with Min-Max bounds and $L_2$-normalization ($\|\vec{v}\|_2 = 1.0$).
- **`REQ-VEC-003` (Inner Product Archetype Matching & Behavioral Risk Scoring)**:
  $$\text{similarity}(\vec{u}, \vec{c}_i) = \vec{u} \cdot \vec{c}_i = -(\vec{u} \mathbin{<\#>} \vec{c}_i)$$
  Persist `behavioral_risk = \max_{i} (\max(0.0, (\vec{u} \cdot \vec{c}_i)) \times w_i)$ to `fraud_entities.behavioral_risk`.
- **`REQ-VEC-004` (PII Masking & Sanitization Layer)**: Pre-process and tokenize sensitive personal data (CPF, names, Pix keys) into opaque tokens (`MASK_USER_TARGET`, `COUNTERPARTY_A`) before prompt generation.
- **`REQ-VEC-005` (Local Llama 3.1 GraphRAG Investigation Synthesizer)**:
  - Query API invoking local LLM engine (vLLM / Ollama with continuous batching and JSON schema mode, `temperature = 0.0`).
  - Output structured `FraudInvestigationDossier` containing executive narrative, risk classification, and evidence breakdown for human compliance analysts.

### Non-Goals
- Synchronous LLM inference during payment transaction authorization.
- Sending unmasked PII or transacting data to public cloud AI APIs.
- Replacing deterministic rules with pure LLM / vector predictions.

---

## 3. Mathematical & Architectural Invariants

- **`I-VEC-001` (Normalized Vector Space)**: All entity feature vectors and archetype centroids MUST be strictly $L_2$-normalized ($\|\vec{v}\|_2 = 1.0$) prior to persistence:
  $$\vec{v}_{\text{norm}} = \frac{\vec{v}}{\sqrt{\sum_{i=1}^{16} v_i^2}}$$
  guaranteeing $\cos(\vec{u}, \vec{v}) = \vec{u} \cdot \vec{v}$.
- **`I-VEC-002` (Durable Co-Location)**: Vector state MUST reside inside PostgreSQL (`pgvector`) to guarantee transactional consistency without managing a separate vector database cluster.
- **`I-VEC-003` (Air-Gapped Local LLM Inference)**: All investigation narrative synthesis MUST execute against local inference infrastructure with zero external data egress.
- **`I-VEC-004` (Strict Schema Adherence)**: The LLM output MUST adhere strictly to a predefined JSON Schema validated programmatically prior to analyst presentation.
- **`I-VEC-005` (Zero Hot-Path Impact)**: Embedding extraction, centroid matching, and dossier generation MUST NOT execute in the synchronous transfer path.

---

## 4. Requirements & Acceptance Criteria

### REQ-VEC-001: Schema & Inner Product Indexing
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS fraud_entity_features (
    entity_id UUID PRIMARY KEY,
    feature_version INT NOT NULL,
    behavioral_vector vector(16) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fraud_archetype_centroids (
    archetype_id VARCHAR(64) PRIMARY KEY, -- e.g. 'MONEY_MULE_RAPID_DRAIN'
    centroid_vector vector(16) NOT NULL,
    risk_weight NUMERIC(3, 2) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fraud_features_hnsw 
ON fraud_entity_features USING hnsw (behavioral_vector vector_ip_ops)
WITH (m = 16, ef_construction = 64);
```

### REQ-VEC-002: Exact 16-D Feature Vector Specification (History 20)
Given aggregated metrics across a 30-day sliding window, compute each raw dimension bounded in $[0.0, 1.0]$:

| Dim | Name | Formula / Bounds | Description |
| :--- | :--- | :--- | :--- |
| **$d_1$** | `tx_frequency` | $\text{clamp}(N_{\text{tx\_30d}} / 500, 0, 1)$ | Monthly transaction count |
| **$d_2$** | `avg_amount` | $\text{clamp}(\bar{A}_{\text{out}} / 50000, 0, 1)$ | Normalized average outgoing ticket |
| **$d_3$** | `amount_std_dev` | $\text{clamp}(\sigma_A / 25000, 0, 1)$ | Volatility / amount standard deviation |
| **$d_4$** | `velocity_spike_1h` | $\text{clamp}(V_{\text{1h\_max}} / 30, 0, 1)$ | Maximum transactions within a 1-hour window |
| **$d_5$** | `nocturnal_ratio` | $N_{\text{22h-06h}} / \max(N_{\text{tx}}, 1)$ | Ratio of transactions during night hours |
| **$d_6$** | `unique_counterparties`| $\text{clamp}(N_{\text{counterparties}} / 100, 0, 1)$ | Unique destination counterparties |
| **$d_7$** | `new_counterparties_7d`| $\text{clamp}(N_{\text{new\_7d}} / 50, 0, 1)$ | Distinct new counterparties in last 7 days |
| **$d_8$** | `rapid_drain_count` | $\text{clamp}(N_{\text{pass\_through}} / 20, 0, 1)$ | Deposit followed by immediate withdrawal |
| **$d_9$** | `fan_out_ratio` | $\text{clamp}(N_{\text{dest}} / N_{\text{sent}}, 0, 1)$ | Outbound dispersion ratio |
| **$d_{10}$** | `fan_in_ratio` | $\text{clamp}(N_{\text{src}} / N_{\text{recv}}, 0, 1)$ | Inbound concentration ratio |
| **$d_{11}$** | `intl_tx_count` | $\text{clamp}(N_{\text{intl}} / 10, 0, 1)$ | International transaction count |
| **$d_{12}$** | `failed_auth_count` | $\text{clamp}(N_{\text{failed\_auth}} / 10, 0, 1)$ | Biometric / auth failure count |
| **$d_{13}$** | `device_switch_count`| $\text{clamp}(N_{\text{devices}} / 5, 0, 1)$ | Distinct hardware/devices used |
| **$d_{14}$** | `out_of_pattern_ratio`| $\text{clamp}(N_{|z| > 3} / N_{\text{tx}}, 0, 1)$ | Ratio of transactions with $Z\text{-Score} > 3$ |
| **$d_{15}$** | `dispute_count` | $\text{clamp}(N_{\text{chargebacks}} / 5, 0, 1)$ | Disputed / chargeback transaction count |
| **$d_{16}$** | `total_amount_volume` | $\text{clamp}(A_{\text{total\_out}} / 200000, 0, 1)$ | Total monthly outgoing volume |

Then apply $L_2$ normalization:
$$\vec{v} = \frac{\vec{d}}{\|\vec{d}\|_2}$$

### REQ-VEC-003: Inner Product Similarity Query
```sql
SELECT 
    entity_id, 
    feature_version, 
    (behavioral_vector <#> :centroidVector) * -1 AS cosine_similarity, 
    updated_at
FROM fraud_entity_features
ORDER BY behavioral_vector <#> :centroidVector ASC
LIMIT 10;
```

### REQ-VEC-004: Local Llama 3.1 GraphRAG Synthesis
- **Given** a flagged entity `userId`.
- **When** `InvestigationSynthesizer.generateDossier(userId)` is invoked.
- **Then**:
  1. Assembles 2-hop graph neighborhood from `SPEC-000.5`, temporal path decay from `SPEC-000.6`, and top archetype matches.
  2. Sanitizes all PII to opaque tokens (`MASK_USER_TARGET`, `COUNTERPARTY_A`).
  3. Dispatches prompt to local Llama 3.1 8B engine with JSON grammar constraint.
  4. Returns validated `FraudInvestigationDossier` JSON with executive summary, risk classification (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`), and chronological evidence trail.
