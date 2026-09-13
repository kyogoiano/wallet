# 📐 Specification: SPEC-000.9.2 — Containerized Multi-Process Topology & Platform Packaging

- **Status**: 🟢 **Ratified**
- **Author**: Antigravity Platform Infrastructure & Edge Resilience Team
- **Date**: 2026-09-10
- **Target Release**: Wallet Service V4 — Phase 000.9.2
- **Bounded Context**: Container Packaging, Deployment Topologies, Spool Storage Provisioning, and Platform Orchestration
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly focused on containerization, topology scaling, storage bindings, and deployment tiers.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history44.txt`](file:///.histories/history44.txt): Evaluated platform portability (Docker Compose, RKE2, Rancher, GKE Standard, Cloud Foundry); established NATS JetStream as the decoupled scaling fabric ($N$ Edge instances $\to$ JetStream $\to$ $M$ Core instances).
  - [`.histories/history45.txt`](file:///.histories/history45.txt): Formulated the Wallet Runtime Contract; analyzed SUSE Virtualization, RKE2, Rancher, and vCluster for private-cloud multi-tenant appliance packaging without vendor lock-in.
  - [`SPEC-000.9.1`](file:///.spec/SPEC-000.9.1-edge-core-independent-runtimes.md): Decoupled Edge and Core into independent OS processes and bootJars.
- **Constitutional Constraints**:
  - `I-EDGE-001` & `I-EDGE-006`: Durable acceptance and local journal fsync scope.
  - `I-PROCESS-001` & `I-PORT-001`: Process isolation and network port segregation.

---

## 1. Intent & Business Value

Following the process separation in `SPEC-000.9.1`, this specification formalizes the **packaging, orchestration, and deployment topology** of the multi-process Wallet Platform.
Because Edge (I/O, rate limiting, SSE, journal fsync) and Core (CPU, PostgreSQL ACID transactions, fraud graph queries) exhibit fundamentally different operational and scaling profiles, they require **asymmetric horizontal scaling** ($N$ Edge $\neq M$ Core) and isolated storage volumes.
This specification packages Edge and Core into discrete OCI container images, defines a cloud-agnostic **Wallet Runtime Contract** (12-Factor config, standard health probes, graceful shutdown), and establishes a **multi-tier deployment matrix** spanning local Docker Compose, on-prem RKE2/Rancher, multi-tenant vCluster appliances, and GKE Standard.

---

## 2. Mathematical & System Invariants

- **`I-CONTAINER-001` (Discrete Minimal OCI Artifacts)**: Edge and Core are packaged into independent OCI container images. `wallet-edge` MUST NOT contain relational database drivers or JPA libraries:
  $$\text{Image}(\text{wallet-edge}) \ne \text{Image}(\text{wallet-core}), \quad \text{JDBCDrivers}(\text{wallet-edge}) = \emptyset$$
- **`I-TOPOLOGY-001` (Asymmetric Scaling Fabric)**: Edge and Core scale with independent replica counts without direct service discovery. NATS JetStream competing consumers distribute traffic across Core replicas:
  $$\text{Replicas}(\text{Edge}) = N, \quad \text{Replicas}(\text{Core}) = M \quad (N \ge 2, M \ge 2, N \neq M)$$
- **`I-STORAGE-001` (Durable Spool Storage Isolation & Volume Profiles)**: Spool journals MUST mount dedicated persistent storage at `/spool`, completely isolated from PostgreSQL database volumes:
  $$\text{Mount}(\text{Edge}_{\text{spool}}) = \text{Option A (Local NVMe HostPath)} \lor \text{Option B (Dedicated CSI Block PV ReadWriteOnce)}$$
- **`I-PLATFORM-001` (Orchestration Neutrality & Runtime Contract)**: Workloads MUST NOT depend on Kubernetes or vCluster APIs, relying strictly on environment variables, standard signals, and HTTP health probes:
  $$\text{Deps}(\text{Workload}) \cap \{\text{KubernetesClient}, \text{vClusterAPI}\} = \emptyset$$
- **`I-GRACEFUL-001` (Coordinated Pod Drain & Spool Flush)**: On SIGTERM, Edge stops accepting ingress, marks readiness `OUT_OF_SERVICE`, flushes in-flight group commits, and halts cleanly within grace period ($T \le 30\text{s}$):
  $$\text{SIGTERM} \to \text{Readiness}=\text{OUT\_OF\_SERVICE} \to \text{force}(\text{Batch}) \to \text{Exit}(0)$$

---

## 3. MoSCoW Requirements

### 3.1 Pillar A: OCI Container Packaging & Multi-Stage Builds [MUST]
- **`REQ-TOP-001` [MUST]**: Provide multi-stage Docker build producing hardened, minimal OCI images for `wallet-edge` (lean WebFlux/Netty, no JDBC) and `wallet-core` (transactional engine) on Java 27.
- **`REQ-TOP-002` [MUST]**: Adhere to 12-Factor principles; all environment-specific configs (`NATS_URL`, `REDIS_HOST`, `DB_URL`, `SPOOL_DIR`) MUST be injected via environment variables.

### 3.2 Pillar B: Tiered Deployment Profiles [MUST]
- **`REQ-TOP-003` [MUST]**: **Tier 0 (Developer/Small Appliance)**: Docker Compose profile running discrete containers (`edge`, `core`, `nats`, `dragonfly`, `postgres`) with health-dependent startup order.
- **`REQ-TOP-004` [MUST]**: **Tier 1 & 2 (Enterprise On-Prem / RKE2)**: Helm charts provisioning separate `Deployment` manifests for Edge and Core, `StatefulSet` for NATS and PostgreSQL, and `PodDisruptionBudget` ($N-1$).
- **`REQ-TOP-005` [MUST]**: **Tier 3 & 4 (vCluster & Public Cloud GKE)**: Workloads MUST deploy into virtual Kubernetes clusters (vCluster) and GKE Standard without code or image modification (`I-PLATFORM-001`).

### 3.3 Pillar C: Storage Volume & Spool Binding [MUST]
- **`REQ-TOP-006` [MUST]**: Manifests MUST provision dedicated persistent volumes for Edge mounted at `/spool` supporting Option A (Node-Local NVMe HostPath for max performance) and Option B (Dedicated CSI Block Volume `ReadWriteOnce` via StatefulSet `volumeClaimTemplates` for cloud pod reschedule reattachment), with zero concurrent-locking overhead (`I-STORAGE-001`).
- **`REQ-TOP-007` [MUST]**: In degraded broker scenarios, SpoolWatermarkGate MUST enforce the 95%/85% hysteresis threshold against the mounted persistent volume (`I-EDGE-005`).

### 3.4 Pillar D: Probes & Coordinated Lifecycle [MUST]
- **`REQ-TOP-008` [MUST]**: Edge and Core expose HTTP probes: `/actuator/health/liveness` and `/actuator/health/readiness`. Edge readiness probe reflects `JournalRecoveryWorker` completion status (`I-EDGE-004`).
- **`REQ-TOP-009` [MUST]**: Container runtimes MUST configure `terminationGracePeriodSeconds` ($\ge 30\text{s}$). Edge catches SIGTERM, marks readiness `OUT_OF_SERVICE`, completes active group commits, and drains (`I-GRACEFUL-001`).

### 3.5 Operational Governance [SHOULD / COULD / WON'T]
- **`REQ-TOP-010` [SHOULD]**: Provide Kubernetes Horizontal Pod Autoscaler (HPA) templates: Edge scales on CPU/RPS; Core scales on NATS JetStream consumer lag.
- **`REQ-TOP-011` [COULD]**: Provide unified Prometheus/Grafana dashboard definitions monitoring multi-pod Edge/Core metrics and NATS queue depths.
- **`REQ-TOP-012` [WON'T]**: Embedded Kubernetes client libraries, CRDs, or custom operator controllers inside the Wallet application runtime.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`edge`** (Container) | Ingress scaling & pod termination | Pod killed during fsync group commit | `I-GRACEFUL-001`: SIGTERM hook flushes active batch before container exit |
| **`core`** (Deployment) | Asymmetric horizontal scaling | Core scaled to 8 replicas with 10 Edge nodes | `I-TOPOLOGY-001`: JetStream competing consumers load-balance dynamically |
| **`storage`** (PV/PVC) | Spillover journal write path | Ephemeral container storage cleared on restart | `I-STORAGE-001`: Dedicated PVC mount at `/spool` survives pod crashes |
| **`nats`** (StatefulSet) | Inter-process message bus | NATS pod rescheduled or restarting | `I-EDGE-001`: Edge spools to persistent volume until NATS recovers |
| **`platform`** (RKE2/GKE) | Multi-cloud / appliance portability | Hardcoded platform APIs break portability | `I-PLATFORM-001`: Strictly 12-Factor environment variable configuration |

---

## 5. Mandatory Test Triad (`I-TDD-002`) & Failure Gates

| Requirement | 1. Positive Canonical Test | 2. Invalid Input / Boundary Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-TOP-001` (`I-CONTAINER-001`) | `ContainerImageVerificationTest.verifyEdgeImage()` | JDBC driver found in `wallet-edge` $\to$ Fail | Root user execution $\to$ Non-root gate fail |
| `REQ-TOP-003` (`I-TOPOLOGY-001`) | `DockerComposeSmokeIT.shouldRunMultiProcessCluster()` | Core down $\to$ Edge remains healthy and accepts | Port conflict $\to$ Abort startup |
| `REQ-TOP-006` (`I-STORAGE-001`) | `SpoolVolumePersistenceIT.shouldPersistAcrossPodCrash()` | Mount missing/read-only $\to$ Edge readiness OUT_OF_SERVICE | Spool full $\to$ Reject degraded acceptance (503) |
| `REQ-TOP-008` (`REQ-TOP-009`) | `GracefulShutdownIT.shouldFlushBatchOnSigterm()` | Kill -9 $\to$ Next pod start executes journal replay | Hanging shutdown $\to$ Force kill at 30s |
| `REQ-TOP-010` (HPA Lag Scaling) | `ScalingTopologyTest.verifyHpaManifests()` | Zero consumer replicas $\to$ NATS buffers without loss | Monolithic scaling $\to$ Fail architecture rule |

---

## 6. Acceptance Criteria

- [ ] Multi-stage Docker builds generate discrete, minimal OCI images for `wallet-edge` and `wallet-core`.
- [ ] Docker Compose environment boots discrete `edge` and `core` containers communicating exclusively via NATS.
- [ ] Helm charts template asymmetric replicas ($N_{\text{edge}} \ne M_{\text{core}}$) and dedicated PV mounts at `/spool`.
- [ ] Zero Kubernetes client libraries or proprietary orchestrator APIs exist in application dependencies (`I-PLATFORM-001`).
- [ ] SIGTERM lifecycle hook proves in-flight group commits are persisted before process termination (`I-GRACEFUL-001`).
- [ ] Spool storage survives pod rescheduling and replays backlogged commands to NATS upon restart (`I-STORAGE-001`).
- [ ] Total lines in this specification do not exceed 250 lines (`I-SDD-006`).
