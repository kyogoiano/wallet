# 📐 Specification: SPEC-000.9.2 — Containerized Multi-Process Topology & Platform Packaging

- **Status**: 🟢 **Ratified**
- **Author**: Antigravity Platform Infrastructure & Edge Resilience Team
- **Date**: 2026-09-10 (Revised: 2026-09-13 per Cost-Tier & On-Prem Review)
- **Target Release**: Wallet Service V4 — Phase 000.9.2
- **Bounded Context**: Container Packaging, Deployment Topologies, Spool Storage Provisioning, and Platform Orchestration
- **Line Budget**: Max 250 lines (`I-SDD-006`). Strictly focused on packaging, scaling, storage bindings, and cost-tiered deployment profiles.

---

## 0. Pre-Flight History & Context Audit

- **Histories Audited**:
  - [`.histories/history45.txt`](file:///.histories/history45.txt): Formulated cost-tiered deployment strategy (Plan A local, Plan B low-cost appliance without K8s, Plan C enterprise Rancher, Plan D GKE).
  - [`.histories/history53.txt`](file:///.histories/history53.txt): Removed invalid $N \ne M$ constraint; formalized independent horizontal scaling; differentiated HostPath vs CSI durability; defined Edge/Core shutdown sequences.
  - [`.histories/history54.txt`](file:///.histories/history54.txt): Ratified sequencing: `000.9.2` (Physical Topology) precedes `000.9.3` (Security Boundary).
  - [`SPEC-000.9.1`](file:///.spec/SPEC-000.9.1-edge-core-independent-runtimes.md): Decoupled Edge and Core into independent OS processes and bootJars.
- **Governing Skills**: [`onprem-infrastructure`](file:///.agents/skills/onprem-infrastructure/SKILL.md) (Rancher, SUSE Virtualization, vCluster, and HCI add-on architecture).

---

## 1. Intent & Business Value

Following the logical separation in `SPEC-000.9.1`, this specification formalizes the **packaging, orchestration, and physical deployment topology** of the Wallet Platform across a **cost-conscious deployment progression**:
1. **Plan A (Local Dev / CI)**: Single-host Docker Compose with zero infrastructure cost.
2. **Plan B (Low-Cost Lean Appliance)**: Non-Kubernetes / lightweight container runtime on commodity bare-metal server or VM, leveraging Docker Compose or free Rancher Open Source capabilities. Slashes control plane RAM/CPU overhead ($<8\text{GB}$ total) and eliminates software licensing.
3. **Plan C (Medium-Cost Enterprise On-Prem / Rancher Full)**: Full-stack SUSE Virtualization (Harvester HCI) + Rancher Open Source + RKE2 + Harvester Add-ons (VM Auto-Balance, Kube-OVN, LVM Local Storage) + vCluster for isolated multi-tenancy.
4. **Plan D (Public Cloud Elastic Scale / GKE)**: Hyperscale GKE Standard with HPA on RPS and consumer lag.

---

## 2. Mathematical & System Invariants

- **`I-CONTAINER-001` (Discrete Hardened OCI Artifacts)**: Edge and Core are packaged into independent OCI container images. `wallet-edge` MUST NOT contain JDBC drivers, JPA/Hibernate, PostgreSQL drivers, or Flyway:
  $$\text{Deps}(\text{Edge}) \cap \{\text{JDBC}, \text{PostgreSQLDriver}, \text{Hibernate}, \text{Flyway}\} = \emptyset, \quad \text{UID}(\text{Process}) \ne 0$$
- **`I-TOPOLOGY-001` (Independent Horizontal Scaling)**: Edge and Core replica counts scale independently without process affinity. Core consumer groups distribute load dynamically:
  $$\text{Replicas}(\text{Edge}) = N, \quad \text{Replicas}(\text{Core}) = M \quad (N \ge 1, M \ge 1; N \text{ and } M \text{ configured independently})$$
- **`I-STORAGE-001` (Single-Writer Spool Ownership)**: Each Edge instance MUST possess exclusive ownership of its `/spool` directory/volume. Concurrent multi-writer mounts are strictly forbidden:
  $$\forall i \ne j, \quad \text{SpoolMount}(\text{Edge}_i) \cap \text{SpoolMount}(\text{Edge}_j) = \emptyset$$
- **`I-STORAGE-002` (Explicit Durability Scope)**: HostPath NVMe provides node-local restart durability while the host survives (no pod-reschedule durability). Dedicated CSI Block Volume (`ReadWriteOnce`) provides reschedule durability where the CSI provider supports dynamic reattachment.
- **`I-MESSAGING-001` (Broker-Mediated IPC)**: Edge MUST NOT invoke Core directly via HTTP or RPC. All cross-boundary command execution is mediated via NATS JetStream:
  $$\text{DirectCalls}(\text{Edge} \to \text{Core}) = \emptyset, \quad \text{Ingress}(\text{Core}) \subset \text{NATS}(\text{commands.wallet.*})$$
- **`I-PLATFORM-001` (Runtime Portability)**: Images MUST NOT embed Kubernetes, vCluster, or orchestrator client libraries (`Deps} \cap \{\text{K8sClient}\} = \emptyset$). Configuration is injected via standard environment variables and mounted files.
- **`I-LIFECYCLE-001` (Edge Shutdown Ordering)**: On SIGTERM, Edge marks readiness `OUT_OF_SERVICE`, halts new ingress, drains in-flight durable acceptance, forces pending journal batches, and exits cleanly within grace period ($T_{\text{grace}} \ge T_{\text{shutdown}} + 10\text{s}$).
- **`I-LIFECYCLE-002` (Core ACK Safety)**: Core MUST NOT acknowledge JetStream delivery before the corresponding financial transaction commits successfully in PostgreSQL.

---

## 3. MoSCoW Requirements

### 3.1 Pillar A: Hardened OCI Packaging & Resource Isolation [MUST]
- **`REQ-TOP-001` [MUST]**: Multi-stage Docker build producing hardened OCI images on Java 27 running as non-root UID ($\ge 10001$). Edge contains zero relational/JDBC dependencies (`I-CONTAINER-001`).
- **`REQ-TOP-002` [MUST]**: 12-Factor external configuration: all environment-specific configs (`NATS_URL`, `DB_URL`, `SPOOL_DIR`) MUST be externally supplied via environment variables or mounted files.
- **`REQ-TOP-003` [MUST]**: Edge and Core deployments MUST support independently configurable CPU/memory requests and limits (`I-TOPOLOGY-001`).

### 3.2 Pillar B: Cost-Tiered Deployment Profiles [MUST]
- **`REQ-TOP-004` [MUST]**: **Plan A (Local Dev & CI / Zero Cost)**: Docker Compose booting discrete `edge`, `core`, `nats`, `dragonfly`, `postgres` on developer workstation with healthcheck dependencies.
- **`REQ-TOP-005` [MUST]**: **Plan B (Low-Cost Lean Appliance / No Heavy K8s)**: Multi-container deployment on single bare-metal host/VM without full Kubernetes control plane tax, using Docker Compose or free Rancher Open Source capabilities (e.g. lightweight node agent / K3s single-node). Direct HostPath NVMe storage (`I-STORAGE-002`), $<8\text{GB}$ RAM total footprint.
- **`REQ-TOP-006` [MUST]**: **Plan C (Medium-Cost Enterprise On-Prem / Rancher Full)**: Helm charts deploying to SUSE Virtualization (Harvester HCI) + Rancher Open Source + RKE2 + Harvester Add-ons (VM Auto-Balance, Kube-OVN, LVM Local Storage for direct NVMe IOPS) + vCluster for isolated multi-tenancy.
- **`REQ-TOP-007` [MUST]**: **Plan D (Public Cloud Elastic Scale / GKE)**: OCI images and Helm charts deploy to GKE Standard / EKS with cloud-managed load balancers, Cloud Persistent Disks, and HPA elasticity without code or image modification (`I-PLATFORM-001`).

### 3.3 Pillar C: Storage Volume & Spool Binding [MUST]
- **`REQ-TOP-008` [MUST]**: Manifests provision dedicated persistent volumes for Edge mounted at `/spool` supporting Option A (Node-Local NVMe HostPath) and Option B (Dedicated CSI Block Volume `ReadWriteOnce`), ensuring single-writer isolation (`I-STORAGE-001`, `I-STORAGE-002`).
- **`REQ-TOP-009` [MUST]**: In degraded broker scenarios, SpoolWatermarkGate MUST enforce 95%/85% hysteresis against the mounted persistent volume (`I-EDGE-005`).

### 3.4 Pillar D: Probes & Coordinated Shutdown Lifecycle [MUST]
- **`REQ-TOP-010` [MUST]**: Edge and Core expose HTTP probes: `/actuator/health/liveness` and `/actuator/health/readiness`. Edge readiness reflects `JournalRecoveryWorker` completion (`I-EDGE-004`).
- **`REQ-TOP-011` [MUST]**: Containers configure `terminationGracePeriodSeconds` ($\ge 30\text{s}$) exceeding application shutdown timeout (20s). Edge executes strict shutdown ordering (`I-LIFECYCLE-001`); Core commits transactions prior to JetStream ACK (`I-LIFECYCLE-002`).

### 3.5 Pillar E: Security & Secret Injection Boundary (Hook for 000.9.3) [MUST]
- **`REQ-TOP-012` [MUST]**: Topology manifests MUST support TLS 1.3-capable ingress listeners and NATS connections. Credentials and secrets MUST be injected via platform-native Secret mounts without baking into images. Core network policies MUST restrict direct public ingress.

### 3.6 Operational Governance [SHOULD / COULD / WON'T]
- **`REQ-TOP-013` [SHOULD]**: Horizontal Pod Autoscaler (HPA) templates: Edge scales on CPU/RPS/active connections; Core scales on NATS consumer lag/processing latency.
- **`REQ-TOP-014` [COULD]**: Unified Prometheus/Grafana dashboard definitions monitoring Edge/Core metrics and NATS queue depths.
- **`REQ-TOP-015` [WON'T]**: Embedded Kubernetes client libraries, CRDs, or custom operators inside application runtimes.

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Module | Affected Flow | Potential Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`edge`** (Container) | Ingress scaling & pod termination | Pod killed during fsync group commit | `I-LIFECYCLE-001`: SIGTERM hook flushes active batch before container exit |
| **`core`** (Deployment) | Independent horizontal scaling | Core scaled to 8 replicas with 3 Edge nodes | `I-TOPOLOGY-001`: JetStream competing consumer group load-balances dynamically |
| **`storage`** (PV/PVC) | Spillover journal write path | Ephemeral container storage cleared on restart | `I-STORAGE-001`, `I-STORAGE-002`: Dedicated volume mount survives container restart |
| **`nats`** (Broker) | Inter-process message bus | NATS pod rescheduled or restarting | `I-MESSAGING-001`: Edge spools locally until NATS recovers; broker decouples runtimes |
| **`platform`** (RKE2/GKE) | Multi-cloud / appliance portability | Hardcoded platform APIs break portability | `I-PLATFORM-001`: 12-Factor external config; zero orchestrator library deps |

---

## 5. Mandatory Test Triad (`I-TDD-002`) & Failure Gates

| Requirement | 1. Positive Canonical Test | 2. Invalid Input / Boundary Gate | 3. Invariant Breach Gate |
| :--- | :--- | :--- | :--- |
| `REQ-TOP-001` (`I-CONTAINER-001`) | `ContainerImageVerificationTest.verifyEdgeImage()` | JDBC driver found in `wallet-edge` $\to$ Fail | Root user execution $\to$ Non-root gate fail |
| `REQ-TOP-005` (Plan B Appliance) | `LowCostApplianceSmokeIT.shouldRunWithoutKubernetes()` | Missing HostPath dir $\to$ Abort fast at boot | Port conflict on 8080/8081 $\to$ Abort |
| `REQ-TOP-008` (`I-STORAGE-001`) | `SpoolVolumePersistenceIT.shouldPersistAcrossPodCrash()` | Mount missing/read-only $\to$ Edge readiness OUT_OF_SERVICE | Spool full $\to$ Reject degraded acceptance (503) |
| `REQ-TOP-011` (`I-LIFECYCLE-001`) | `GracefulShutdownIT.shouldFlushBatchOnSigterm()` | Kill -9 $\to$ Next start executes recovery replay | Hanging shutdown $\to$ Force kill after grace period |
| `REQ-TOP-013` (HPA Lag Scaling) | `ScalingTopologyTest.verifyHpaManifests()` | Zero consumer replicas $\to$ NATS buffers without loss | Monolithic scaling $\to$ Fail architecture rule |

---

## 6. Acceptance Criteria

- [ ] Multi-stage Docker builds generate discrete, hardened OCI images for `wallet-edge` (non-root, no JDBC) and `wallet-core`.
- [ ] Plan B lean appliance operates reliably on single Linux host/VM without Kubernetes, using Docker Compose or free Rancher (`REQ-TOP-005`).
- [ ] Edge and Core scale independently ($N \ge 1, M \ge 1$) without process affinity (`I-TOPOLOGY-001`).
- [ ] Helm charts template dedicated single-writer persistent volumes for Edge at `/spool` (`I-STORAGE-001`).
- [ ] SIGTERM lifecycle hook proves in-flight group commits are persisted before process termination (`I-LIFECYCLE-001`).
- [ ] Core commits financial transaction to PostgreSQL before ACKing JetStream message (`I-LIFECYCLE-002`).
- [ ] Zero Kubernetes client libraries or proprietary orchestrator APIs exist in application dependencies (`I-PLATFORM-001`).
- [ ] Total lines in this specification do not exceed 250 lines (`I-SDD-006`).
