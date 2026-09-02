# 🏁 Implementation Summary: SPEC-000.6 — Fraud Risk Propagation, Temporal Decay & Hardened Hand-Rolled Job Engine

- **Spec Reference**: [`SPEC-000.6-fraud-risk-propagation-and-temporal-decay.md`](file:///.spec/SPEC-000.6-fraud-risk-propagation-and-temporal-decay.md)
- **Plan Reference**: [`PLAN-000.6-fraud-risk-propagation-and-temporal-decay.md`](file:///.spec/PLAN-000.6-fraud-risk-propagation-and-temporal-decay.md)
- **Tasks Reference**: [`TASKS-000.6-fraud-risk-propagation-and-temporal-decay.md`](file:///.spec/TASKS-000.6-fraud-risk-propagation-and-temporal-decay.md)
- **Author**: Antigravity Financial & Risk Engineering Team
- **Date**: 2026-09-01
- **Status**: 🟢 **Implemented & Verified**

---

## 1. Executive Summary & Capabilities Delivered

This milestone implements the **Fraud Risk Propagation & Temporal Decay Engine** with a **hardened hand-rolled PostgreSQL job engine** (`fraud_propagation_jobs` using `FOR UPDATE SKIP LOCKED` and token leases), eliminating external workflow engine complexity while guaranteeing complete durability, deterministic reproducibility, and zero financial transfer path overhead.

### Key Architectural Deliverables
1. **Mathematical Model & Exponential Decay**:
   - `PropagationConfig`: Configurable 7-day half-life ($t_{1/2} = 7\text{ days}$, $\lambda = \frac{\ln 2}{t_{1/2}}$), calibrated edge weights (`OWNS = 0.95`, `SHARED_DEVICE = 0.90`, `SHARED_PHONE = 0.85`, `SHARED_EMAIL = 0.75`, `TRANSFERRED_TO = 0.60`, `USES = 0.50`, `SHARED_IP = 0.35`, `LOGGED_FROM = 0.10`, `SHARES = 0.10`).
   - `PathInfluenceCalculator`: Deterministic decay calculation relative to explicit `as_of` timestamps using historical evidence from `fraud_relationship_events.occurred_at`.
   - `MultiPathAggregator`: Probabilistic union aggregation ($R_{\text{propagated}} = 1 - \prod (1 - I_p)$) preventing artificial risk inflation from duplicate paths.
2. **PostgreSQL Path Traversal & Dimension Isolation**:
   - `PostgresRiskPropagationDao`: Recursive CTE path queries with acyclic cycle breakers and max hop boundaries (`max_hops <= 3`).
   - `fraud_entities` update strictly isolates dimensions: updates `propagated_risk`, `propagation_model_version`, and `propagation_evaluated_at` with zero mutation to `direct_risk`, `graph_risk`, `behavioral_risk`, or `final_risk` (`I-PROP-005`).
3. **Hardened Hand-Rolled Job Queue & Worker Pool**:
   - `fraud_propagation_jobs`: Durable queue table with `as_of`, `worker_token`, `lease_until`, `attempt_count`, `available_at`.
   - Partial unique index `idx_fraud_prop_active_unique` on `(entity_id, model_version)` where `status IN ('PENDING', 'RUNNING', 'RETRY_WAIT')` enforcing database-level active job deduplication (`I-PROP-007`).
   - `PropagationJobWorker`: High-concurrency worker claiming jobs via `SELECT ... FOR UPDATE SKIP LOCKED` with 2-minute token leases and scheduled reaper recovery (`I-PROP-008`).
4. **Transactional Outbox Alert Delivery**:
   - `EntityRiskPropagationAlertEvent` (type `RISK_PROPAGATION_DETECTED` on subject `events.fraud.propagation`) staged in `outbox` table during the same DB transaction when $R_{\text{propagated}} \ge 0.60$ (`I-OUTBOX-001`, `I-PROP-009`).
5. **REST API Exposure**:
   - `POST /api/v1/fraud/intelligence/propagation/dispatch/{entityId}`: Enqueue background evaluation job.
   - `GET /api/v1/fraud/intelligence/propagation/evaluate/{entityId}`: Synchronous diagnostic evaluation for investigations.

---

## 2. Practical Verification Guide (Seed Data Gate — `I-SDD-002`)

### 2.1. Seed Data Fixtures
Execute the following SQL in PostgreSQL to insert a test syndicate scenario:

```bash
docker exec -i wallet-postgres psql -U wallet -d wallet -c "
-- High-Risk Syndicate Root (Alice: direct_risk = 1.0)
INSERT INTO fraud_entities (id, entity_type, direct_risk, graph_risk, behavioral_risk, propagated_risk, final_risk, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'USER', 1.0, 0.0, 0.0, 0.0, 1.0, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET direct_risk = 1.0;

-- Intermediate Mule (Bob: direct_risk = 0.0)
INSERT INTO fraud_entities (id, entity_type, direct_risk, graph_risk, behavioral_risk, propagated_risk, final_risk, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'USER', 0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Target Mule (Charlie: direct_risk = 0.0)
INSERT INTO fraud_entities (id, entity_type, direct_risk, graph_risk, behavioral_risk, propagated_risk, final_risk, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333333', 'USER', 0.0, 0.0, 0.0, 0.0, 0.0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Edge 1: Alice -> Bob (SHARED_DEVICE, 7 days ago -> Half-Life factor 0.50)
INSERT INTO fraud_relationship_events (id, source_id, target_id, relationship_type, occurred_at)
VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'SHARED_DEVICE', NOW() - INTERVAL '7 days');

-- Edge 2: Bob -> Charlie (TRANSFERRED_TO, 7 days ago -> Half-Life factor 0.50)
INSERT INTO fraud_relationship_events (id, source_id, target_id, relationship_type, occurred_at)
VALUES (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'TRANSFERRED_TO', NOW() - INTERVAL '7 days');"
```

---

### 2.2. Test Scenario 1: Diagnostic Propagation Evaluation (cURL)
Evaluate outwards risk propagation from Alice:

```bash
curl -s -X GET "http://localhost:8080/api/v1/fraud/intelligence/propagation/evaluate/11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" | jq .
```

**Expected JSON Output**:
```json
{
  "rootSourceId": "11111111-1111-1111-1111-111111111111",
  "evaluatedAt": "2026-09-01T18:30:00Z",
  "pathsEvaluated": 2,
  "modelVersion": "v1",
  "propagatedRisks": {
    "22222222-2222-2222-2222-222222222222": {
      "entityId": "22222222-2222-2222-2222-222222222222",
      "propagatedRisk": 0.45,
      "shortestHopCount": 1,
      "primaryRelationship": "SHARED_DEVICE",
      "evaluatedAt": "2026-09-01T18:30:00Z"
    },
    "33333333-3333-3333-3333-333333333333": {
      "entityId": "33333333-3333-3333-3333-333333333333",
      "propagatedRisk": 0.135,
      "shortestHopCount": 2,
      "primaryRelationship": "SHARED_DEVICE",
      "evaluatedAt": "2026-09-01T18:30:00Z"
    }
  }
}
```

---

### 2.3. Test Scenario 2: Asynchronous Job Dispatch & Worker Processing (cURL)
Dispatch an asynchronous propagation evaluation job:

```bash
curl -s -X POST "http://localhost:8080/api/v1/fraud/intelligence/propagation/dispatch/11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" | jq .
```

**Expected HTTP Status**: `202 Accepted`  
**Expected JSON Output**:
```json
{
  "jobId": "e1f2a3b4-5678-90ab-cdef-111122223333",
  "entityId": "11111111-1111-1111-1111-111111111111",
  "status": "PENDING",
  "modelVersion": "v1",
  "message": "Propagation job successfully enqueued"
}
```

---

### 2.4. Test Scenario 3: Database Job Status & Risk State Validation (SQL)
Verify job completion and database records:


```bash
docker exec -i wallet-postgres psql -U wallet -d wallet -c "
-- 1. Check Job Queue Status (Transitions PENDING -> RUNNING -> COMPLETED)
SELECT id, entity_id, status, attempt_count, worker_token, lease_until, completed_at
FROM fraud_propagation_jobs
WHERE entity_id = '11111111-1111-1111-1111-111111111111';"
```


```bash
docker exec -i wallet-postgres psql -U wallet -d wallet -c "
-- 2. Verify Dimension Isolation in fraud_entities (propagated_risk updated, direct_risk untouched)
SELECT id, direct_risk, graph_risk, behavioral_risk, propagated_risk, final_risk, propagation_model_version, propagation_evaluated_at
FROM fraud_entities
WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333'
);"
```

---

## 3. Compliance Traceability Matrix

| Invariant / Requirement | Component / Class | Verification Status |
| :--- | :--- | :--- |
| `REQ-PROP-001` (Edge Weights) | [`PropagationConfig`](file:///fraud/src/main/java/br/com/wallet/fraud/intelligence/propagation/PropagationConfig.java) | 🟢 Verified |
| `REQ-PROP-002` (Half-Life Decay & `as_of`) | [`PathInfluenceCalculator`](file:///fraud/src/main/java/br/com/wallet/fraud/intelligence/internal/propagation/PathInfluenceCalculator.java) | 🟢 Verified |
| `REQ-PROP-003` (Multi-Hop Bounded Traversal) | [`PostgresRiskPropagationDao`](file:///fraud/src/main/java/br/com/wallet/fraud/intelligence/internal/propagation/PostgresRiskPropagationDao.java) | 🟢 Verified |
| `REQ-PROP-004` (Multi-Path Probabilistic Union) | [`MultiPathAggregator`](file:///fraud/src/main/java/br/com/wallet/fraud/intelligence/internal/propagation/MultiPathAggregator.java) | 🟢 Verified |
| `REQ-PROP-005` (Risk Dimension Isolation) | [`DefaultRiskPropagationEngine`](file:///fraud/src/main/java/br/com/wallet/fraud/intelligence/internal/propagation/DefaultRiskPropagationEngine.java) | 🟢 Verified |
| `REQ-PROP-006` (Token Leases & DB Uniqueness) | [`PostgresPropagationJobDao`](file:///fraud/src/main/java/br/com/wallet/fraud/intelligence/internal/propagation/PostgresPropagationJobDao.java) | 🟢 Verified |
| `REQ-PROP-007` (Transactional Outbox Alert) | [`EntityRiskPropagationAlertEvent`](file:///src/main/java/br/com/wallet/ledger/api/event/EntityRiskPropagationAlertEvent.java) | 🟢 Verified |
| `I-PROP-001` to `I-PROP-009` | All Engine Components & Invariants | 🟢 Verified |
