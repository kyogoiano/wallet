# 📝 Task Breakdown: TASKS-000.9.2 — Containerized Multi-Process Topology & Platform Packaging

- **Associated Spec**: [`../SPEC-000.9.2-containerized-multi-process-topology.md`](file:///.spec/SPEC-000.9.2-containerized-multi-process-topology.md)
- **Associated Plan**: [`../plans/PLAN-000.9.2-containerized-multi-process-topology.md`](file:///.spec/plans/PLAN-000.9.2-containerized-multi-process-topology.md)
- **Status**: ✅ Completed
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-TOP-001` (`I-CONTAINER-001`) | `[MUST]` | `ContainerImageVerificationTest.verifyEdgeClasspathAndPackaging()` | `TASK-TOP-1.1`, `TASK-TOP-1.2` |
| `REQ-TOP-002` (`I-CONTAINER-001`) | `[MUST]` | `ContainerImageVerificationTest.verifyNonRootExecution()` | `TASK-TOP-1.1`, `TASK-TOP-1.2` |
| `REQ-TOP-003` (`I-TOPOLOGY-001`) | `[MUST]` | `ScalingTopologyTest.verifyIndependentScaling()` | `TASK-TOP-5.1` |
| `REQ-TOP-004` (Plan A Dev/CI) | `[MUST]` | `LowCostApplianceSmokeIT.verifyPlanAComposeDefaults()` | `TASK-TOP-2.1`, `TASK-TOP-2.2` |
| `REQ-TOP-005` (Plan B Lean Appliance) | `[MUST]` | `LowCostApplianceSmokeIT.shouldRunWithoutKubernetes()` | `TASK-TOP-2.1`, `TASK-TOP-2.2` |
| `REQ-TOP-006` (Plan C Harvester HCI) | `[MUST]` | `HelmManifestValidationTest.verifyHarvesterLvmStorageClass()` | `TASK-TOP-3.1`, `TASK-TOP-3.2` |
| `REQ-TOP-007` (Plan D GKE Elastic) | `[MUST]` | `HelmManifestValidationTest.verifyGkeDeploymentProfile()` | `TASK-TOP-3.1` |
| `REQ-TOP-008` (`I-STORAGE-001`, `I-STORAGE-002`) | `[MUST]` | `SpoolVolumePersistenceIT.shouldPersistAcrossPodCrash()` | `TASK-TOP-3.2` |
| `REQ-TOP-009` (`I-EDGE-005`) | `[MUST]` | `SpoolWatermarkGateTest.verifyHysteresisThresholds()` | `TASK-TOP-3.3` |
| `REQ-TOP-010` (`REQ-TOP-011`, `I-LIFECYCLE-001`) | `[MUST]` | `GracefulShutdownIT.shouldFlushBatchOnSigterm()` | `TASK-TOP-4.1`, `TASK-TOP-4.2` |
| `REQ-TOP-011` (`I-LIFECYCLE-002`) | `[MUST]` | `CoreCommandConsumerAckTest.shouldAckOnlyAfterFinancialEffect()` | `TASK-TOP-4.1` |
| `REQ-TOP-012` (Port/Security Boundary) | `[MUST]` | `ProcessBoundaryArchitectureTest.verifyPlatformPortability()` | `TASK-TOP-1.3`, `TASK-TOP-5.2` |
| `REQ-TOP-013` (HPA Elasticity) | `[SHOULD]` | `HelmManifestValidationTest.verifyHpaSpecifications()` | `TASK-TOP-3.1` |
| `REQ-TOP-015` (`I-PLATFORM-001`) | `[MUST]` | `ProcessBoundaryArchitectureTest.verifyNoKubernetesDependencies()` | `TASK-TOP-1.3` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing a task, focus strictly on the active task card below. Do not load unrelated modules into memory.

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Hardened Multi-Stage Container Packaging (`I-CONTAINER-001`, `I-PLATFORM-001`)
- [x] `TASK-TOP-1.1` [MUST]: Update `dockerfile` multi-stage build:
  - Configure `edge` stage: create dedicated non-root user `wallet` (UID 10001:10001), pre-create `/spool` directory with ownership `10001:10001` and permissions `700`, switch to `USER 10001:10001`, and copy `wallet-edge.jar`.
  - Configure `core` stage: install `libstdc++ libgomp curl` for ONNX C++ runtime, create non-root user `wallet` (UID 10001:10001), switch to `USER 10001:10001`, and copy `wallet-core.jar`.
  - Ensure JVM options `-Duser.timezone=UTC` and memory limits are respected.
- [x] `TASK-TOP-1.2` [MUST]: Implement `ContainerImageVerificationTest`:
  - Assert that `wallet-edge.jar` runtime classpath contains zero references to `org.postgresql:postgresql`, `spring-boot-starter-data-jpa`, `org.hibernate`, `com.zaxxer:HikariCP`, or `org.flywaydb:flyway-core` (`I-CONTAINER-001`).
  - Assert that `dockerfile` defines `USER 10001:10001` for both runtime stages and does not execute any process as root.
- [x] `TASK-TOP-1.3` [MUST]: Implement `PlatformPortabilityArchitectureTest` (or extend `ProcessBoundaryArchitectureTest`):
  - ArchUnit check asserting zero imports of Kubernetes/vCluster/orchestrator SDK client libraries (`io.fabric8..`, `io.kubernetes..`, etc.) across all production packages (`I-PLATFORM-001`).

### Phase 2: Plan A & Plan B Orchestration (Local & Low-Cost Appliance)
- [x] `TASK-TOP-2.1` [MUST]: Orchestrate Plan A (Local Dev) & Plan B (Low-Cost Lean Appliance):
  - Refine `docker-compose.yaml` (Plan A): ensure non-root container constraints, service healthcheck dependency chaining, and dedicated named volume `edge-spool`.
  - Create `docker-compose.appliance.yaml` (Plan B Lean Appliance): multi-container topology running directly on host without Kubernetes control plane tax, using direct NVMe HostPath bind mounts (`./spool-data:/spool`), resource memory reservations and limits (total footprint < 8GB RAM), and production environment variables.
- [x] `TASK-TOP-2.2` [MUST]: Implement `LowCostApplianceSmokeIT`:
  - Validates Plan B appliance runtime configuration: verifies direct HostPath directory mounting, non-root permissions compatibility, and healthiness of discrete Edge and Core processes operating without Kubernetes.

### Phase 3: Plan C & Plan D Kubernetes & Storage Orchestration (Harvester HCI / vCluster / GKE)
- [x] `TASK-TOP-3.1` [MUST]: Author Helm Charts under `deploy/helm/wallet-platform`:
  - Base Helm chart structure (`Chart.yaml`, `values.yaml`).
  - Deployments: `templates/edge-deployment.yaml` ($N$ replicas, `/spool` volume mount, non-root securityContext `runAsUser: 10001`, `runAsNonRoot: true`), `templates/core-deployment.yaml` ($M$ replicas, port 8081, non-root securityContext).
  - Services: `templates/edge-service.yaml` (HTTP 8080, UDP 8443 QUIC), `templates/core-service.yaml` (ClusterIP headless internal only).
  - Storage: `templates/edge-pvc.yaml` supporting Option A (HostPath) and Option B (`StorageClass: harv-lvm-local` / CSI RWO).
  - Governance: `templates/poddisruptionbudget.yaml`, `templates/networkpolicy.yaml` (isolating Core port 8081), `templates/hpa.yaml` (`EdgeHPA` on CPU/RPS, `CoreHPA` on NATS consumer lag).
  - Values profiles: `values-harvester.yaml` (Plan C Harvester HCI / vCluster with LVM local storage), `values-gke.yaml` (Plan D GKE with Cloud SSD).
- [x] `TASK-TOP-3.2` [MUST]: Implement `SpoolVolumePersistenceIT`:
  - Verifies that when an Edge instance is restarted against an existing mounted persistent volume directory, unACKed segments are recovered by `JournalRecoveryWorker` and drained to NATS JetStream without data loss (`I-STORAGE-001`, `I-STORAGE-002`).
- [x] `TASK-TOP-3.3` [MUST]: Verify `SpoolWatermarkGate` Hysteresis (`I-EDGE-005`):
  - Verify existing unit test `SpoolWatermarkGateTest` enforces 95% rejection (HTTP 503) and 85% resumption hysteresis against the mounted persistent volume.

### Phase 4: Coordinated Shutdown Lifecycle & ACKs
- [x] `TASK-TOP-4.1` [MUST]: Coordinated Shutdown Lifecycle Hooks (`I-LIFECYCLE-001`, `I-LIFECYCLE-002`):
  - Ensure Edge graceful shutdown hooks set `EdgeReadinessHealthIndicator` to `OUT_OF_SERVICE`, reject new ingress with HTTP 503, flush active group commit batch (`FileChannel.force(false)`), and drain worker before process exit (`I-LIFECYCLE-001`).
  - Verify Core JetStream consumer ACK safety: messages are ACKed only after PostgreSQL transaction commits successfully (`I-LIFECYCLE-002`).
- [x] `TASK-TOP-4.2` [MUST]: Implement `GracefulShutdownIT`:
  - Test verifying that SIGTERM signal to running Edge process triggers orderly drain and forces in-flight group commit batches to disk before container termination.

### Phase 5: Independent Scaling & Boundary Isolation
- [x] `TASK-TOP-5.1` [MUST]: Implement `ScalingTopologyTest` (`I-TOPOLOGY-001`):
  - Verifies independent horizontal scaling ($N \ge 1, M \ge 1$, e.g. $N=2$ Edge and $M=3$ Core instances) where multiple Core consumers process commands concurrently from shared NATS stream `commands.wallet.*` without process affinity and without duplicate ledger entries.
- [x] `TASK-TOP-5.2` [MUST]: Implement `HelmManifestValidationTest`:
  - Validates generated Helm manifests against Kubernetes schemas: asserts non-root execution (`runAsUser: 10001`), Core port isolation in `NetworkPolicy`, independent replica configurations, and proper StorageClass bindings.

### Phase 6: Operational Verification & Convergence
- [x] `TASK-TOP-6.1` [MUST]: Author `SUMMARY-000.9.2.md`:
  - Bi-directional equivalence reconciliation (`I-SDD-003`).
  - Practical Verification Guide with reproducible CLI/cURL commands, Plan A/B compose commands, seed data fixtures, and expected responses (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

### 4.1 Architectural Invariant Gates
- [x] `wallet-edge` image contains zero JDBC/JPA/PostgreSQL dependencies (`I-CONTAINER-001`)
- [x] Both Edge and Core run under dedicated non-root UID 10001 (`I-CONTAINER-001`)
- [x] Edge and Core scale independently ($N \ge 1, M \ge 1$) without affinity (`I-TOPOLOGY-001`)
- [x] Edge journal exclusively locks `/spool` directory (`I-STORAGE-001`)
- [x] Plan B runs without Kubernetes control plane on direct HostPath NVMe (<8GB RAM) (`REQ-TOP-005`)
- [x] Helm chart templates LVM Local Storage for Harvester HCI (`REQ-TOP-006`)
- [x] Edge SIGTERM forces pending journal batch to disk before container exit (`I-LIFECYCLE-001`)
- [x] Core commits transaction to PostgreSQL before ACKing NATS message (`I-LIFECYCLE-002`)
- [x] Zero Kubernetes client libraries present in application classpath (`I-PLATFORM-001`)
