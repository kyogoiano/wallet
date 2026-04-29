2. `antifraud_v3_production_ready_architecture.md`

# 🛡️ Wallet Service — Fraud & Risk Engine (V3)

## 🧭 Overview

This document introduces a Fraud & Risk Engine for the Wallet Service.

The engine is designed to:

- Detect suspicious financial behavior
- Prevent abuse (ATO, cash-out, mule accounts)
- Maintain O(1) evaluation per request
- Preserve stateless API while enabling controlled statefulness

---

## 🧠 Design Principles

### 1. Non-invasive to core domain

The ledger remains the source of truth.

Fraud engine acts as a pre-execution gate:

```

request → fraud engine → decision → execute use case

```

---

### 2. O(1) evaluation (hot path)

- No scans
- Sliding window + aggregation
- Precomputed state

---

### 3. Multi-layer state model

| Layer        | Scope | Consistency | Purpose |
|-------------|------|------------|--------|
| Local       | Pod  | Strong     | Fast checks |
| Distributed | Redis| Eventual   | Global behavior |
| Async       | NATS | Eventual   | Enrichment |

---

## 🧱 Architecture Extension

```

Client → API → FraudEngine → Decision
↘
NATS → Worker → DB
→ Outbox → NATS
→ FraudEnricher → Redis

```

---

## 🛡️ Fraud Types Covered

### 1. Account Takeover (ATO)

- New device/IP
- Credential change

Rule:
```

recent credential change + high value transfer → BLOCK

```

---

### 2. Cash-Out (Account Draining)

- Large transfer
- Rapid sequence
- New recipient

Rule:
```

sum(last 30s) > threshold → BLOCK

```

---

### 3. Mule Accounts

- Deposit → immediate transfer
- High turnover

Rule:
```

low retention time → increase risk

```

---

### 4. Velocity Attacks

- Many operations in short time

Rule:
```

count(last N seconds) > threshold

```

---

### 5. Behavior Anomaly

- Deviation from baseline

Rule:
```

current_amount >> avg_amount

````

---

## ⚙️ Rule Categories

### 🟢 Local Rules (O(1))

- Sliding window (value)
- Operation count
- New recipient detection

Example:

```java
if (opsLast10s > 20) BLOCK;
````

---

### 🟡 Global Rules (Redis)

Eventually consistent.

Examples:

```
user:{id}:daily_volume
user:{id}:risk_score
user:{id}:unique_recipients
```

---

### 🔵 Async Rules (NATS)

* Aggregations
* Behavior enrichment
* Pattern detection

---

## 🧠 Scoring Engine

### Score Calculation

```java
int score = 0;

if (isNewDevice) score += 3;
if (isNewRecipient) score += 5;
if (isHighAmount) score += 7;
if (isVelocitySpike) score += 4;
if (isLowRetention) score += 6;
```

---

### Decision

```java
if (score >= 12) BLOCK;
else if (score >= 7) REVIEW;
else ALLOW;
```

---

## ⚡ Sliding Window Strategy

### Local

* Ring buffer
* LongAdder aggregation

---

### Global

Keys:

```
user:{id}:bucket:{timestamp}
```

TTL:

```
expire after window
```

---

## 🔁 Event-Driven Enrichment

### Events

```
transaction.requested
transaction.approved
transaction.blocked
```

---

### Consumers

#### FraudEnricher

* Updates Redis
* Maintains aggregates

#### RiskAggregator

* Builds long-term metrics

---

## 🧠 State Evolution

### Phase 1

* Stateless
* Local rules

### Phase 2

* Sliding window
* Score engine

### Phase 3

* Redis global state

### Phase 4

* Async enrichment (NATS)

---

## ⚠️ Trade-offs

| Choice               | Impact         |
| -------------------- | -------------- |
| Eventual consistency | Acceptable     |
| Redis dependency     | Extra infra    |
| Local state          | Possible drift |

---

## 🔐 Safety Guarantees

* Fraud engine never mutates ledger
* Only blocks or delays
* Idempotency preserved

---

## 🚀 Future Extensions

* Graph-based fraud detection
* ML models
* Device fingerprinting
* Geo anomaly detection

---

## 🧭 Final Insight

Fraud detection is probabilistic.

Goal:

```
maximize detection
minimize false positives
```