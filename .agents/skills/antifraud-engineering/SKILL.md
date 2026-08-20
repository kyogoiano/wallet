---
name: antifraud-engineering
description: Rules, sliding windows, scoring engine, Redis Lua scripts, and multi-tier state management for the Fraud & Risk Engine (V3).
---

# 🛡️ Anti-Fraud & Risk Engine (V3) Skill

## 1. Identity & Objective

This skill guides the design, implementation, and maintenance of the **Fraud & Risk Engine (V3)** in `:fraud`. It guarantees $O(1)$ fraud checks, multi-tier state caching (Caffeine L1 + Redis L2 + NATS L3), and resilient fail-closed / fail-open rules.

---

## 2. Multi-Layer State Model

| Tier | Technology | Consistency | Purpose | Scope |
| :--- | :--- | :--- | :--- | :--- |
| **L1 (Local)** | Caffeine + `SlidingAmountWindow` | Strong | In-memory microsecond checks, velocity buckets | Per pod |
| **L2 (Distributed)** | Redis (Unix Socket / TCP) | Eventual | Global user blocklist, cross-pod velocity, risk scores | Cluster-wide |
| **L3 (Async)** | NATS JetStream | Eventual | Event aggregation, behavior profiling, risk projection | Background workers |

---

## 3. Fraud Engine Core Rules

### 1. Account Takeover (ATO)
- **Signal**: New device / IP + high-value transaction.
- **Action**: Immediate `BLOCK` or elevated risk score.

### 2. Velocity Attacks (`GlobalVelocityRule`)
- **Signal**: More than $N$ transactions in sliding window $W$ (e.g. $> 10$ ops in 30s).
- **Mechanism**: Redis Sorted Set (`zadd NX` / `zremrangebyscore` / `zcard`) via atomic Lua script in `RedisVelocityStore.java`.

### 3. User Blocklist (`UserBlockRule`)
- **Signal**: User marked as blocked in Redis (`user:{userId}:blocked`).
- **Optimization**: Negative caching in Caffeine with differential TTLs (10 min if blocked, 30s if unblocked) via `RedisUserStore.java`.

---

## 4. Scoring Engine & Decision Matrix

```java
int score = 0;
if (isNewDevice) score += 3;
if (isNewRecipient) score += 5;
if (isHighAmount) score += 7;
if (isVelocitySpike) score += 4;
if (isLowRetention) score += 6;

if (score >= 12) return FraudDecision.BLOCK;
if (score >= 7)  return FraudDecision.REVIEW;
return FraudDecision.ALLOW;
```

---

## 5. Lua Scripting & Atomicity Invariants

- All Redis multi-step operations (e.g. replay check + counter increment + score evaluation) must be executed in atomic Lua scripts (see `RedisScripts.java`).
- Lua scripts must handle idempotency using the client `operationId`.
- Scripts must set explicit TTLs on keys to prevent Redis memory leaks.
