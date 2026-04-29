# 🏗️ Fraud & Risk Engine V3 — Production Ready Architecture

## 🧭 Overview

This document describes the production-ready architecture of the Fraud Engine integrated into Wallet Service.

Focus:

- High throughput
- Low latency
- Event-driven consistency
- Horizontal scalability

---

## 🧱 High-Level Architecture

```mermaid
flowchart TD

Client --> API

API --> FraudEngine

FraudEngine -->|ALLOW| NATS
FraudEngine -->|BLOCK| Reject
FraudEngine -->|REVIEW| ReviewQueue

NATS --> WalletWorker --> DB[(PostgreSQL)]

DB --> Outbox --> NATS

NATS --> FraudEnricher --> Redis[(Global State)]
````

---

## 🧠 Core Components

### 1. FraudEngine (Synchronous)

Responsibilities:

* Evaluate transaction risk
* Execute local rules
* Compute score
* Return decision

Constraints:

* O(1) execution
* No blocking IO

---

### 2. Local State (Per Pod)

Structure:

* Sliding window (ring buffer)
* LongAdder counters

Properties:

* Fast
* Non-blocking
* Eventually inconsistent across pods

---

### 3. Redis (Global State)

Responsibilities:

* Cross-pod aggregation
* User-level metrics
* Risk scoring support

Data model:

```
user:{id}:daily_volume
user:{id}:risk_score
user:{id}:bucket:{timestamp}
```

---

### 4. NATS (Event Backbone)

Responsibilities:

* Event propagation
* Decoupling
* Async enrichment

Event types:

```
transaction.requested
transaction.approved
transaction.blocked
```

---

### 5. FraudEnricher (Async)

Responsibilities:

* Consume events
* Update Redis
* Compute aggregates

---

## ⚙️ Data Flow

### Request Path (Hot Path)

```
1. Request arrives
2. FraudEngine evaluates (local + cached global)
3. Decision:
   - ALLOW → proceed
   - BLOCK → reject
   - REVIEW → queue
```

---

### Async Path

```
1. Event published to NATS
2. FraudEnricher consumes
3. Redis updated
4. Future decisions improved
```

---

## 🧠 Consistency Model

| Component | Consistency      |
| --------- | ---------------- |
| Local     | Strong (per pod) |
| Redis     | Eventual         |
| NATS      | Eventual         |

---

## ⚡ Performance Strategy

### Hot Path

* In-memory only
* No network calls
* O(1) operations

---

### Data Structures

* Arrays > Maps
* LongAdder > AtomicLong (under contention)

---

### CPU Optimization

* Cache-friendly structures
* Minimal branching
* Predictable execution

---

## ⚠️ Failure Scenarios

### Redis Down

* System falls back to local rules
* Reduced accuracy, not availability

---

### NATS Delay

* Slower enrichment
* No impact on request latency

---

### Pod Restart

* Local state lost
* Rebuilt via traffic + async updates

---

## 🧪 Scaling Strategy

### Horizontal Scaling

* Stateless API pods
* Shared Redis
* NATS cluster

---

### Partitioning Strategy

* User-based partitioning
* Sticky routing (optional optimization)

---

## 🔐 Safety Guarantees

* Ledger consistency preserved
* Fraud engine is side-effect free
* No impact on financial correctness

---

## 🚀 Future Enhancements

* Distributed token leasing (stronger limits)
* Graph fraud detection
* ML scoring pipeline
* Real-time anomaly detection (stream processing)

---

## 🧭 Final Insight

This architecture enables:

* Stateless core
* Stateful intelligence
* High throughput with controlled risk

```
fast decisions locally
better decisions globally (eventually)
```