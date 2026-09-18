# 📐 Architecture Plan: PLAN-000.9.2 — Containerized Multi-Process Topology & Platform Packaging

- **Associated Spec**: [`../SPEC-000.9.2-containerized-multi-process-topology.md`](file:///.spec/SPEC-000.9.2-containerized-multi-process-topology.md)
- **Governing Architecture**: [`../architecture/ARCH-000.9-reactive-edge-and-multi-process-topology.md`](file:///.spec/architecture/ARCH-000.9-reactive-edge-and-multi-process-topology.md)
- **Status**: 🟢 **Approved**
- **Author**: Antigravity Platform Infrastructure & Edge Resilience Guild
- **Date**: 2026-09-13
- **Target Modules**: `:edge` (`wallet-edge.jar`), Root Application (`wallet-core.jar`), Container Infrastructure (`dockerfile`, `docker-compose.yaml`, `deploy/helm/wallet-platform`)
- **Architectural Scope**: Enforces independent horizontal scaling (`I-TOPOLOGY-001`), discrete hardened OCI packaging (`I-CONTAINER-001`), single-writer spool volume isolation (`I-STORAGE-001`, `I-STORAGE-002`), broker-mediated IPC (`I-MESSAGING-001`), coordinated shutdown lifecycles (`I-LIFECYCLE-001`, `I-LIFECYCLE-002`), and security plumbing hooks for Phase 000.9.3 (`REQ-TOP-014`).
- **Governing Skills**: [`onprem-infrastructure`](file:///.agents/skills/onprem-infrastructure/SKILL.md) (Rancher, SUSE Virtualization, vCluster, and HCI add-on architecture).

---

## 1. Technical Strategy & Macro Topology

`PLAN-000.9.2` physicalizes the logical separation established in Phase 000.9.1. Edge and Core exhibit fundamentally different operational, resource, and failure characteristics:
- **Edge Gateway (`wallet-edge`)**: I/O-bound reactive Netty runtime handling external HTTP/3 (QUIC) and HTTP/2 ingress, rate limiting, and local segmented journal `fsync`. Zero relational persistence dependencies (`I-CONTAINER-001`). Scales on network concurrency, active SSE streams, and request rate.
- **Core Financial Engine (`wallet-core`)**: CPU- and transaction-bound engine executing ACID ledger mutations, `SELECT FOR UPDATE` account locks, fraud graphs, and smart savings sweeps. Headless, zero public HTTP ingress. Scales on NATS JetStream consumer lag and processing latency.

```mermaid
flowchart TD
    subgraph External["External Network / Ingress"]
        LB["Load Balancer / Cloud Ingress<br/>Port 8080 (REST/SSE) / Port 8443 UDP (QUIC)"]
    end

    subgraph EdgeCluster["Edge Gateway Tier (Stateless, Zero-DB)"]
        direction TB
        E1["wallet-edge Pod 1<br/>Non-Root (UID 10001)"]
        E2["wallet-edge Pod 2<br/>Non-Root (UID 10001)"]
        EN["wallet-edge Pod N<br/>Non-Root (UID 10001)"]
        
        subgraph SpoolVolumes["Dedicated Single-Writer Persistent Volumes (/spool)"]
            V1[("PV 1 (RWO)<br/>Single Writer")]
            V2[("PV 2 (RWO)<br/>Single Writer")]
            VN[("PV N (RWO)<br/>Single Writer")]
        end
        E1 --- V1
        E2 --- V2
        EN --- VN
    end

    subgraph MessagingFabric["Broker-Mediated IPC (I-MESSAGING-001)"]
        NATS["NATS JetStream Cluster (TLS 1.3)<br/>Stream: commands (commands.wallet.*)<br/>Stream: operations (operations.status.*)"]
    end

    subgraph CoreCluster["Core Financial Transaction Tier (Headless)"]
        direction TB
        C1["wallet-core Pod 1<br/>Port 8081 (Internal Mgmt)"]
        C2["wallet-core Pod 2<br/>Port 8081 (Internal Mgmt)"]
        CM["wallet-core Pod M<br/>Port 8081 (Internal Mgmt)"]
    end

    subgraph PersistenceTier["ACID Persistence Stores"]
        PG[("PostgreSQL 17/19<br/>Ledger & Accounts")]
        DF[("DragonflyDB Cluster<br/>Hot Cache & Velocity")]
    end

    LB -->|Round-Robin Ingress| EdgeCluster
    EdgeCluster -->|Publish commands.wallet.*| NATS
    NATS -->|Competing Consumer Group wallet-core-workers| CoreCluster
    CoreCluster -->|Publish operations.status.*| NATS
    NATS -.->|Status Fanout| EdgeCluster
    CoreCluster -->|Row Locks & ACID Commits| PG
    CoreCluster -->|O 1 Hot Cache| DF
```

---

## 2. Packaging & Container Hardening Design

### 2.1 Multi-Stage OCI Dockerfile
The repository Dockerfile (`dockerfile`) uses multi-stage builds producing hardened, minimal OCI images on Eclipse Temurin / Valhalla JDK 27:

```dockerfile
# Stage 1: Build
FROM oraclelinux:9-slim AS builder
# ... Compile :bootJar and :edge:bootJar) ...

# Stage 2: Hardened Edge Runtime (wallet-edge)
FROM oraclelinux:9-slim AS edge
RUN useradd -u 10001 -m -s /bin/sh wallet && \
    mkdir -p /spool && chown -R 10001:10001 /spool && chmod 700 /spool
USER 10001:10001
WORKDIR /app
COPY --from=builder --chown=10001:10001 /app/edge/build/libs/wallet-edge.jar app.jar
VOLUME ["/spool"]
EXPOSE 8080 8443/udp
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

# Stage 3: Hardened Core Runtime (wallet-core)
FROM oraclelinux:9-slim AS core
RUN microdnf install -y libstdc++ libgomp curl && microdnf clean all && \
    useradd -u 10001 -m -s /bin/sh wallet
USER 10001:10001
WORKDIR /app
COPY --from=builder --chown=10001:10001 /app/build/libs/wallet-core.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Duser.timezone=UTC", "--enable-native-access=ALL-UNNAMED", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

### 2.2 Strict Classpath Boundary Assertions (`I-CONTAINER-001`)
Automated test `ContainerImageVerificationTest` validates that `wallet-edge.jar` contains zero references to:
- `org.postgresql:postgresql`
- `spring-boot-starter-data-jpa` / Hibernate
- `com.zaxxer:HikariCP`
- `org.flywaydb:flyway-core`

---

## 3. Storage Architecture & Single-Writer Isolation

### 3.1 Single-Writer Spool Ownership (`I-STORAGE-001`)
- Each `wallet-edge` container exclusively owns its mounted `/spool` directory.
- `SegmentedFileJournal` acquires an OS-level file lock on `${SPOOL_DIR}/.spool.lock` via `FileChannel.tryLock()`.
- If a container is rescheduled or cloned and attempts to mount an active directory, startup halts with `IllegalStateException`.

### 3.2 Dual Storage Profile Durability Scopes (`I-STORAGE-002`)
| Profile | Volume Mechanism | Durability Guarantee | Target Topology |
| :--- | :--- | :--- | :--- |
| **Option A (HostPath NVMe)** | Host NVMe bind mount | Node-local restart durability while host survives. No pod-reschedule durability. | Bare-metal high-throughput appliances (Profile P1). |
| **Option B (Dedicated CSI RWO Block)** | Dedicated Cloud PV (`ReadWriteOnce`) | Pod-reschedule durability where CSI provider re-attaches volume to replacement pod. | Enterprise Cloud & Kubernetes clusters (Profile P2, P3). |

### 3.3 Hysteresis Gate (`I-EDGE-005`)
In broker-degraded scenarios, `SpoolWatermarkGate` monitors disk capacity:
- Rejects new incoming commands (`HTTP 503 SERVICE_UNAVAILABLE`) when `/spool` usage $\ge 95\%$.
- Resumes command admission only after background recovery drains usage below $85\%$.

---

## 4. Cost-Tiered Deployment Profiles Matrix

```text
               ┌────────────────────────────────────────────────────────────────────────┐
               │                      Cost-Tiered Deployment Profiles                   │
               └───────┬────────────────┬──────────────────────┬────────────────────────┘
                       │                │                      │
                       ▼                ▼                      ▼
                 ┌──────────┐     ┌───────────┐          ┌───────────┐
                 │  Plan A  │     │  Plan B   │          │  Plan C   │ ──> Plan D (GKE)
                 │ Local Dev│     │ Low-Cost  │          │ Enterprise│
                 │ Compose  │     │ Non-K8s   │          │ Rancher/HC│
                 └──────────┘     └───────────┘          └───────────┘
```

### 4.1 Plan A: Local Dev & CI (`docker-compose.yaml` / Zero Infrastructure Cost)
- Runs discrete containers on bridge network `wallet-net`:
  - `wallet-edge`: Ingress port `8080`, UDP `8443`, isolated named volume `edge-spool`.
  - `wallet-app`: Core port `8081`, depends on `nats`, `dragonfly`, `postgres`, `otel-collector`.
  - Infrastructure: `postgres` (pgvector 18), `dragonfly` (v1.40.1 UDS + TCP), `nats` (JetStream enabled), `otel-collector`, `openobserve`.
- Healthcheck orchestration: Core checks `/actuator/health`; Edge checks `/actuator/health/readiness`.

### 4.2 Plan B: Low-Cost Lean On-Prem Appliance (Non-K8s / Free Rancher Capabilities)
- **Target**: Branch appliances, single bare-metal servers, and budget-constrained deployments.
- **Cost Minimization**: Avoids full Kubernetes control plane tax (no multi-node etcd, no API server overhead; saves $>8\text{GB}$ RAM and 4+ CPU cores).
- **Orchestration**: Runs multi-container stack via Docker Compose or free Rancher Open Source capabilities (e.g. lightweight node agent / single-node K3s).
- **Storage**: Direct HostPath NVMe mounts at `/spool` for Edge and direct disk for PostgreSQL (`Option A`, `I-STORAGE-002`). Zero storage licensing, zero network SAN latency.
- **Footprint**: Fits comfortably in 4–8 vCPUs and 8–16 GB RAM total.

### 4.3 Plan C: Medium-Cost Enterprise On-Prem (Rancher Full + SUSE Virtualization / Harvester HCI)
- **Target**: Regional enterprise data centers, regulated private banking clouds, multi-tenant appliances.
- **Orchestration**: Full SUSE Virtualization (Harvester HCI) + Rancher Open Source + RKE2 + Harvester Add-ons:
  - **VM Auto-Balance**: Live migration shifting Core/NATS workloads non-disruptively during node maintenance.
  - **Kube-OVN**: Distributed firewall, micro-segmentation isolating Core port `8081` (`REQ-TOP-012`).
  - **LVM Local Storage**: Direct NVMe IOPS for `/spool` and PostgreSQL, bypassing Longhorn 3x network replication penalty.
  - **vCluster**: Isolated virtual Kubernetes control planes per tenant inside a shared RKE2 guest cluster.
- **Manifests**: Helm chart under `deploy/helm/wallet-platform` with independent Edge/Core deployments and PodDisruptionBudget.

### 4.4 Plan D: Public Cloud Elastic Scale (GKE Standard / EKS)
- **Target**: Hyperscale multi-region deployments with elastic autoscaling.
- **Orchestration**: GKE Standard with cloud-managed load balancers, Cloud Persistent Disks (`ReadWriteOnce`), and Horizontal Pod Autoscalers:
  - `EdgeHPA`: Scales on CPU ($>70\%$), active connections, and HTTP RPS.
  - `CoreHPA`: Scales on NATS JetStream consumer lag (`commands.wallet.*` backlog $> 500$ msgs).

---

## 5. Coordinated Shutdown Lifecycle Contracts

### 5.1 Grace Period Sizing (`REQ-TOP-009`)
Containers configure `terminationGracePeriodSeconds: 30`. Application shutdown deadline is configured to 20s (`spring.lifecycle.timeout-per-shutdown-phase: 20s`), ensuring a 10s safety buffer before Kubernetes issues `SIGKILL`.

### 5.2 Edge Shutdown Ordering (`I-LIFECYCLE-001`)
```mermaid
sequenceDiagram
    autonumber
    participant K8s as Orchestrator / K8s
    participant Edge as EdgeApplication
    participant Spool as SegmentedFileJournal
    participant NATS as NATS JetStream

    K8s->>Edge: SIGTERM Signal
    Edge->>Edge: 1. Set EdgeReadinessHealthIndicator to OUT_OF_SERVICE
    Edge->>Edge: 2. Reject new ingress connections (HTTP 503)
    Edge->>Spool: 3. Complete in-flight durable acceptance requests
    Edge->>Spool: 4. Force pending group commit batch (FileChannel.force)
    Edge->>NATS: 5. Drain JournalRecoveryWorker & close NATS connection
    Edge->>K8s: 6. Process Exits cleanly (code 0)
```

### 5.3 Core Transaction-Commit ACK Safety (`I-LIFECYCLE-002`)
During shutdown:
1. Core pauses pulling new messages from JetStream consumer.
2. For messages currently processing: Core finishes domain logic, commits PostgreSQL transaction (`SELECT FOR UPDATE`), and sends NATS `Message.ack()`.
3. If PostgreSQL fails or crashes, message is NOT ACKed and remains available in JetStream for peer replicas.

---

## 6. Security & Secret Injection Boundary (Hook for 000.9.3)

`REQ-TOP-014` establishes the platform infrastructure required by Phase 000.9.3:
1. **TLS 1.3 Termination**: Ingress manifests and Docker Compose support TLS certificates mounted into `/etc/ssl/certs/`.
2. **Secret Externalization**: Secrets (`NATS_TOKEN`, `SPRING_DATASOURCE_PASSWORD`, future HMAC keys) are injected via platform-native Kubernetes Secrets / environment variables, never committed to git or baked into OCI layers.
3. **Core Network Isolation**: Core container exposes port `8081` internally only. Public Ingress routes `/operations/*` strictly to `wallet-edge`.

---

## 7. Architecture Decision Records (ADRs)

### ADR-000.9.2-01: Discrete Hardened OCI Containers with Non-Root Execution
- **Context**: Running containers as root introduces severe container breakout vulnerabilities. Packaging single images with conditional binaries violates `I-CONTAINER-001`.
- **Decision**: Multi-stage build producing discrete `wallet-edge` (lean, no JDBC) and `wallet-core` images running under dedicated UID `10001:10001`.
- **Consequence**: Full compliance with enterprise container security gates and `readOnlyRootFilesystem` compatibility.

### ADR-000.9.2-02: Independent Horizontal Scaling via NATS Competing Consumers
- **Context**: Prior specs mandated $N \ne M$, which was an arbitrary and mathematically flawed restriction. Edge and Core exhibit different resource bottlenecks.
- **Decision**: Formalize $N \ge 1, M \ge 1$ as independent variables (`I-TOPOLOGY-001`). Core replicas join JetStream competing consumer group `wallet-core-workers` without process affinity.
- **Consequence**: Operators can run $(N=2, M=2)$, $(N=5, M=2)$, or $(N=2, M=10)$ depending on write vs processing loads.

### ADR-000.9.2-03: Single-Writer Spool Isolation & Explicit Durability Scopes
- **Context**: The journal requires single-writer access (`ConcurrentWriters = 1`). Cloud block volumes and host local storage offer different failure characteristics.
- **Decision**: Document explicit durability guarantees: HostPath provides node-local restart durability; CSI RWO provides pod-reschedule durability where supported (`I-STORAGE-002`). `FileChannel.tryLock()` serves as an active safety barrier against accidental multi-mount.

### ADR-000.9.2-04: Strict Shutdown Ordering with Core ACK Durability
- **Context**: Rolling container upgrades risk dropping in-flight journal writes or premature NATS message ACKs before PostgreSQL commits.
- **Decision**: Enforce coordinated shutdown ordering for Edge (`I-LIFECYCLE-001`) and assert Core commits transactions before ACKing (`I-LIFECYCLE-002`).

---

## 8. Failure Modes & Boundary Resilience

| Scenario | Immediate Detection | System Impact | Automated Recovery / Mitigation |
| :--- | :--- | :--- | :--- |
| **Core Process Crash / OOM** | NATS consumer heartbeat drops | Zero impact on Edge ingress | Edge continues accepting commands while broker/spool capacity permits. Replacement Core resumes consumer group (`I-PROCESS-001`). |
| **Edge Container Restart** | K8s liveness failure | Pod restarts on same node/volume | `JournalRecoveryWorker` scans `/spool`, recovers unACKed segments, and drains to NATS (`I-EDGE-004`). |
| **Spool Mount Conflict** | `.spool.lock` lock attempt fails | Pod fails fast at boot | Second pod halts with `IllegalStateException`, preventing split-brain corruption (`I-STORAGE-001`). |
| **NATS Broker Outage** | `BrokerCircuitBreaker` trips `OPEN` | Ingress switches to local spool | Edge writes commands to 64MB segments with group-commit `fsync`, returning `202 ACCEPTED` (`I-EDGE-001`). |
| **Graceful Shutdown Timeout** | K8s sends SIGTERM | Readiness set `OUT_OF_SERVICE` | Edge flushes pending journal batch and drains within 20s. K8s allows 30s grace before SIGKILL (`I-LIFECYCLE-001`). |

---

## 9. Zero Spec-Drift Verification Plan

1. **Packaging & Boundary Verification**:
   - `ContainerImageVerificationTest`: Inspects `wallet-edge.jar` classpath to assert absence of JDBC/JPA/Postgres classes, and inspects Dockerfile layers for non-root UID `10001`.
2. **Topology & Orchestration Verification**:
   - `DockerComposeSmokeIT`: Boots multi-container environment via Testcontainers / Compose, verifying independent execution and end-to-end transfer acceptance.
   - `ScalingTopologyTest`: Asserts JetStream competing consumer distribution across multiple Core instances ($M \ge 2$) from multiple Edge nodes ($N \ge 2$) without message duplication.
3. **Storage & Durability Verification**:
   - `SpoolVolumePersistenceIT`: Simulates container restart and verifies segment recovery from mounted persistent volume.
4. **Lifecycle & Shutdown Verification**:
   - `GracefulShutdownIT`: Emits SIGTERM to running Edge process and verifies that in-flight group commit batches are fully forced to disk before shutdown.
