# SPEC-FUTURE-001 — PostgreSQL 19 Vectorized Execution Evaluation (`pg_vexec`)

- **Status:** Proposed — Future Evaluation
- **Priority:** `[COULD]`
- **Target Milestone:** PostgreSQL 19 Adoption Review
- **Bounded Context:** Intelligence & Analytical Platform
- **Baseline:** PostgreSQL 18 + `pgvector`
- **Execution Policy:** Pure research/benchmark harness; zero production deployment authorized.
- **Date:** 2026-10-11

---

## 1. Context and Motivation

The Wallet Service deploys vector workloads across two distinct architectural tiers:
1. **Tier A (16-D Behavioral Vectors — SPEC-000.7)**: Compact $L_2$-normalized transactional embeddings in `fraud_entity_features` and calibrated archetypes in `fraud_archetype_centroids` ($N \le 20$). The logical payload of 16 `float32` values is 64 bytes (matching common L1 cache-line and AVX-512 register widths, though physical on-disk tuples include Postgres varlena headers). Centroid matching uses exact inner product (`<#>`).
2. **Tier B (768-D Copilot RAG — SPEC-004.1)**: High-dimensional semantic retrieval over financial proposal audit trails and compliance narratives.

PostgreSQL 19 and experimental extensions (`pg_vexec`, `vexec_pgvector`) introduce block-vectorized query execution (replacing Volcano tuple-by-tuple iteration with columnar chunk processing).

To avoid confounding variables, this evaluation strictly disentangles three distinct mechanisms:
- **Vectorized Executor**: Chunked batch processing vs Volcano tuple iteration (`pg_vexec`).
- **SIMD Math Kernels**: Hardware-accelerated distance math (`vexec_pgvector` AVX-512 / AVX2).
- **Index Algorithms**: Exact sequential scans vs approximate nearest neighbor graph traversal (HNSW).

## 2. Architectural Invariants

- **`I-FUTURE-001` (Zero Production Impact)**: Experiments run exclusively on isolated benchmark environments. No production database may deploy pre-release or preview extensions.
- **`I-FUTURE-002` (Hot-Path Isolation)**: The transactional ledger (`I-LEDGER-001`) and the synchronous fraud authorization gate (`I-FRAUD-002`, DragonflyDB $P99 < 2\text{ms}$) remain 100% decoupled from experimental vector engines. Vector queries run strictly in asynchronous workers (`fraud_embedding_jobs`) or investigative tools.
- **`I-FUTURE-003` (Numerical & Semantic Equivalence)**:
  - *Exact Scans*: ID parity and score equivalence within floating-point tolerance ($\epsilon \le 10^{-5}$) with deterministic tie-breaking.
  - *Approximate Scans*: Calibrated `Recall@k \ge 0.98` against exact ground truth.
- **`I-FUTURE-004` (Multi-Tenant Boundary & Security Isolation)**: Benchmarks must enforce tenant predicates (`WHERE tenant_id = ?`). Tests must verify zero cross-tenant leakage, correct handling of empty results, and characterize sparse masking performance penalties under high filter selectivity.
- **`I-FUTURE-005` (Hardware & Virtualization Disambiguation)**: Benchmarks must differentiate hardware capabilities (AVX-512 vs AVX2), query planner decisions, and engine execution paths (vectorized chunk vs scalar fallback).
- **`I-FUTURE-006` (Decoupled Workload Adoption)**: Adoption decisions are workload-specific. Sub-optimal performance on small-table centroid matching does not invalidate adoption for high-dimensional analytical retrieval.

## 3. Non-Goals

- Replacing `pgvector` as the canonical vector type and storage extension.
- Deploying unverified beta/preview software to production on-premises appliances.
- Assuming SIMD distance acceleration improves HNSW pointer-chasing graph traversal.
- Confounding embedding generation inference costs with database query execution times.

## 4. Evaluation Gates & Prerequisites

### Gate A — Ecosystem & Release Maturity
- Official GA releases of PostgreSQL 19, `pgvector`, and `pg_vexec` / `vexec_pgvector` (recording exact commit/build hashes).
- Operational review of required server configurations (e.g., `shared_preload_libraries`), backup/restore tools (`pg_dump`), and connection poolers.

### Gate B — Appliance & Virtualization Compatibility
Evaluate two deployment profiles on SUSE Virtualization / Harvester HCI (KubeVirt):
1. **Performance Profile**: Host CPU instruction passthrough (`cpu.mode: host-passthrough`) exposing AVX-512 and AVX2.
2. **Compatibility / Migration Profile**: Standardized CPU model supporting live VM migration across heterogeneous cluster nodes.
- *Verification Requirement*: Benchmark harnesses must inspect `EXPLAIN (ANALYZE, BUFFERS)` and engine telemetry to prove whether the query ran via vectorized SIMD or fell back to scalar execution. Lack of `SIGILL` alone does not prove SIMD execution.

### Gate C — Workload Qualification & Testbed Isolation
- **Tier A Testbed**: Representative data populated from existing `fraud_entity_features` schemas.
- **Tier B Testbed**: Dedicated isolated experimental dataset (e.g., `benchmark_proposal_embeddings vector(768)`) without altering production schemas. Vector generation is precomputed to isolate database query execution.

## 5. Workload Taxonomy & Benchmark Matrix

Compare three environments under identical hardware:
- **C0**: PostgreSQL 18 + `pgvector` (current production baseline).
- **C1**: PostgreSQL 19 + `pgvector` (isolates PostgreSQL 19 core improvements).
- **C2**: PostgreSQL 19 + `pgvector` + `pg_vexec` / `vexec_pgvector` (experimental).

### Workload Suite
- **W1 (Centroid Matching — $D=16$, $N \le 20$)**: Measure vector batch setup overhead vs native sequential scan for small reference tables.
- **W2a (Mass Exact Scan — $D=16$, $N=10^6$)**: Exact inner product (`<#>`) flat scan comparing Volcano iteration vs vectorized execution across C0, C1, and C2.
- **W2b (Approximate Search — $D=16$, $N=10^6$)**: HNSW index traversal measuring latency vs `Recall@k` across equivalent index parameters.
- **W3 (Semantic Retrieval — $D=768$, $N=10^5$)**: Hybrid relational filter + cosine similarity (`<=>`) over proposal audit narratives.
- **W4 (Multi-Tenant Selectivity & Security Matrix)**:
  - *Selectivity Curve*: Measure latency and sparse mask overhead across tenant predicate selectivities (0.1%, 1%, 10%, 100%).
  - *Adversarial Boundaries*: Assert zero cross-tenant leakage across skew distributions and empty tenant queries.

## 6. Measurements & Acceptance Criteria

### Experimental Controls
- Hardware parity (fixed CPU allocation, RAM, and NVMe storage).
- Separate warm-cache and controlled cold-cache runs.
- Concurrency measured by active PostgreSQL server backend connections, not client virtual threads.
- Multi-iteration runs recording statistical distributions (P50, P95, P99, mean, standard deviation).
- Profiling of CPU utilization, memory footprint (RSS), and L1/L2 cache misses.

### Decision Thresholds
- **Adoption Criteria**: Repeatable $\ge 30\%$ latency reduction on a designated priority workload (e.g., W2a or W3) under warm cache, without degrading tenant isolation or increasing P99 tail latency on other operations.
- **Workload Granularity**: A negative result on W1 (due to chunk setup overhead) triggers a "Reject for Centroids" decision while allowing adoption for W2a or W3.
- **Rejection Triggers**: Any execution abort (`SIGILL`), tenant boundary bypass, memory leak, or operational constraint preventing live migration on Harvester clusters when required.

## 7. Deliverables & Definition of Done

- [ ] GA status and exact build versions verified for PostgreSQL 19 and extensions.
- [ ] Hardware instruction profiles (Performance vs Live Migration) benchmarked on Harvester.
- [ ] Execution path disambiguation verified (SIMD vs scalar fallback verified via query plans).
- [ ] Workloads W1, W2a, W2b, W3, and W4 executed under controlled statistical conditions.
- [ ] Multi-tenant isolation verified with zero cross-tenant leakage.
- [ ] Numerical parity validated ($\epsilon \le 10^{-5}$ for exact; `Recall@k \ge 0.98` for ANN).
- [ ] Architecture Decision Record (ADR) published with workload-specific recommendations (Adopt, Defer, or Reject).