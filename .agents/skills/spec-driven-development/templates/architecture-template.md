# 🏛️ System Architecture: ARCH-XXX — [Title]

- **Status**: 🟢 Ratified | 🟡 Draft | ⚪ Deprecated
- **Author**: Antigravity Architecture Team
- **Date**: YYYY-MM-DD
- **Target Systems / Subprojects**: [e.g. :edge, :core, :fraud, infrastructure]
- **Governing Specs**: [`../SPEC-XXX.md`](file:///.spec/SPEC-XXX.md)

---

## 1. Executive Summary & Architectural Mantra

> *[One-sentence architectural mantra capturing the core philosophy]*

[High-level overview of the subsystem, its role in the financial platform, and the engineering problems it solves.]

---

## 2. Macro Topology & Component Interaction

```mermaid
graph TD
    %% Mermaid architectural diagram showing components, boundaries, protocols
```

[Detailed narrative explaining the topology, trust boundaries, and network flows.]

---

## 3. Detailed Subsystem Specifications

### 3.1 Subsystem A: [e.g. Process Isolation & Network Ingress]
- **Component Design**:
- **Protocol & Serialization**:
- **Port & Resource Segregation**:

### 3.2 Subsystem B: [e.g. Storage, Journaling & Persistence]
- **Storage Topology**: [Local NVMe vs Dedicated CSI Block Volume ReadWriteOnce]
- **Framing & Data Structures**:
- **Durability & Fsync Semantics**:

### 3.3 Subsystem C: [e.g. IPC Fabric & Messaging]
- **Broker Quorum & Scaling Fabric**:
- **Subject Conventions & Deduplication**:
- **Baggage & Trace Propagation**:

---

## 4. Failure Modes, Resilience & Disaster Recovery

| Failure Scenario | Immediate Detection | System Impact | Automated Recovery / Mitigation |
| :--- | :--- | :--- | :--- |
| **Broker Outage** | ... | ... | ... |
| **Node / Pod Crash** | ... | ... | ... |
| **Database Pool Freeze**| ... | ... | ... |
| **Storage Corruption** | ... | ... | ... |

---

## 5. Deployment Topology & Scaling Matrix

| Deployment Tier | Infrastructure Target | Edge Replicas | Core Replicas | Storage Binding | Orchestration / Management |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 0 (Dev)** | Docker Compose | 1 | 1 | Named Volume | Docker Engine |
| **Tier 1 (Small)**| RKE2 Bare-Metal | 2 | 2 | HostPath / Local PV | Helm |
| **Tier 2 (Enterprise)**| RKE2 / Rancher | 4+ | 2+ | CSI Block Volume (RWO)| Rancher Managed |
| **Tier 3 (Multi-Tenant)**| RKE2 + vCluster | Asymmetric | Asymmetric | CSI Block Volume (RWO)| vCluster per Tenant |
| **Tier 4 (Cloud)**| GKE Standard | Auto (HPA) | Auto (HPA) | Cloud SSD Persistent Disk | Kubernetes |

---

## 6. Authoritative Invariants Registry

| Invariant ID | Name | Mathematical / Formal Definition | Enforcement Mechanism |
| :--- | :--- | :--- | :--- |
| `I-XXX-001` | ... | ... | ... |
