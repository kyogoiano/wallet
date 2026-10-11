# 📋 Specification: SPEC-000.1 — In-Memory Store Migration: Redis to DragonflyDB

- **Status**: Implemented / Verified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-26
- **Target Release / Milestone**: Infrastructure Baseline V3.1
- **Domain Module**: `br.com.wallet.fraud`, `br.com.wallet.infrastructure`

---

## 1. Intent & Business Value

The Wallet Service currently relies on **Redis** for distributed anti-fraud state, velocity counters, sliding windows, user blocklist caching, and atomic Lua script evaluation (`REVIEW_COUNT_PROTECTED_SCRIPT`, `BLOCK_PROTECTED_SCRIPT`).

As transaction volume scales, single-threaded Redis architecture becomes a bottleneck for multi-core scalability and tail latency under high concurrency. **DragonflyDB** is a drop-in, multi-threaded in-memory store designed for modern hardware that delivers up to 25x higher throughput and sub-millisecond tail latencies while maintaining 100% wire-compatibility with the Redis RESP2/RESP3 protocol.

**Core Objectives**:
1. Seamlessly migrate the in-memory cache/state layer from Redis to **DragonflyDB** (`docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1`).
2. Verify full compatibility with the existing Lettuce driver, RESP3 protocol, and Unix Domain Socket (UDS) / TCP connection pipelines.
3. Validate atomic execution of existing Lua scripts under Dragonfly's multi-threaded lock manager.
4. Update container configurations, Testcontainers, and test suites to validate regression-free operation.

---

## 2. Scope & Non-Goals

### In Scope
- **DragonflyDB Deployment Configuration**: Update `docker-compose.yaml` to deploy DragonflyDB with UDS (`/var/run/redis/redis.sock`) and TCP (`6379`) options.
- **Client Configuration & Protocol Compatibility**: Ensure Lettuce `RedisConfig` connects reliably with RESP3, connection timeouts, keepalive, and reconnect policies against DragonflyDB.
- **Lua Script Compatibility Verification**: Verify `REVIEW_COUNT_PROTECTED_SCRIPT` and `BLOCK_PROTECTED_SCRIPT` run atomically and deterministically on DragonflyDB without cross-thread lock violations.
- **Test Infrastructure Upgrade**: Update `IntegrationTestBase.java` Testcontainers setup to execute integration tests against DragonflyDB.
- **Performance & Latency Regression Tests**: Automated test suites verifying sliding windows, velocity counters, blocklist lookups, and replay protection.

### Non-Goals
- Altering the business logic or threshold algorithms in `br.com.wallet.fraud`.
- Introducing non-standard DragonflyDB extensions (e.g. JSON/Search) that would break backward compatibility with Redis.
- Modifying PostgreSQL ledger tables, transactional outbox, or NATS JetStream infrastructure.

---

## 3. Mathematical & System Invariants

- **`I-DF-001` (RESP3 Wire Protocol Equivalence)**: All commands dispatched via Lettuce (`GET`, `SET`, `INCR`, `INCRBY`, `PSETEX`, `EXISTS`, `ZADD`, `HSET`, `EXPIRE`, `EVAL`) MUST return behaviorally and semantically identical results as Redis 8+.
- **`I-DF-002` (Lua Script Multi-Key Atomicity)**: All Lua scripts executed in DragonflyDB MUST declare 100% of their touched keys inside the `KEYS[...]` argument array to guarantee atomic cross-thread shard locking without race conditions or deadlocks.
- **`I-DF-003` ($O(1)$ Hot Path Latency)**: Fraud rule evaluation, velocity counter increments, and block checks on the hot transaction path MUST maintain $O(1)$ time complexity and sub-millisecond execution.
- **`I-DF-004` (Zero Replay Invariant)**: Replay detection via `fraud:op:{operationId}` in Lua scripts MUST guarantee that concurrent execution of the same `operationId` returns `REPLAY_DETECTED` for all executions after the first.

---

## 4. Functional Requirements

- **`REQ-DF-001` (Dragonfly Container Integration)**: The application and Docker Compose environment MUST run DragonflyDB as the primary in-memory store with healthchecks and persistent volume support.
- **`REQ-DF-002` (Dual Connection Mode)**: The connection layer MUST support both high-speed Epoll Unix Domain Sockets (`/var/run/redis/redis.sock`) and TCP fallback (`localhost:6379`).
- **`REQ-DF-003` (Atomic Lua Review Script Execution)**: `REVIEW_COUNT_PROTECTED_SCRIPT` MUST atomically increment review counters, accumulate risk scores, and set user blocked state when thresholds are breached.
- **`REQ-DF-004` (Atomic Lua Block Script Execution)**: `BLOCK_PROTECTED_SCRIPT` MUST atomically set the user blocked flag with optional TTL and protect against replaying operations.
- **`REQ-DF-005` (Sliding Window & Velocity Compatibility)**: `GlobalVelocityRule`, `SlidingWindowRule`, `NewRecipientRule`, and `UserBlockRule` MUST operate without changes or degradation.
- **`REQ-DF-006` (Testcontainers Integration)**: Integration test suites (`FraudIT`, `FraudReactionIT`, `AccountBlockingIT`, etc.) MUST execute against DragonflyDB in CI and local test runs.

---

## 5. Non-Functional Requirements

- **Performance**: P99 latency for fraud gate checks under $1.0\text{ ms}$ over Unix Domain Sockets; throughput headroom increased on multi-core systems.
- **Resilience**: Client auto-reconnect on disconnect, commands rejected during disconnect rather than queued unboundedly (`REJECT_COMMANDS`).
- **Observability**: Metrics and logs from DragonflyDB integrated into monitoring dashboards; OpenTelemetry baggage maintained across fraud state transitions.

---

## 6. Interface Contracts & Data Key Models

### Key Patterns in DragonflyDB
| Key Pattern | Type | TTL | Purpose |
| :--- | :--- | :--- | :--- |
| `fraud:op:{operationId}` | `String` | $30\text{s}$ (ms via `PSETEX`) | Operation replay protection |
| `user:{userId}:blocked` | `String` | Persistent / Optional TTL | Immediate blocklist cache |
| `user:{userId}:review_count`| `String (Int)` | $86400\text{s}$ ($24\text{h}$) | Suspicious operation counter |
| `user:{userId}:risk_score` | `String (Int)` | $86400\text{s}$ ($24\text{h}$) | Accumulated risk score |
| `user:{userId}:tx_timeline` | `Sorted Set (ZSET)` | $30\text{ days}$ | Transaction timestamp history for velocity checks |
| `tx:{operationId}` | `Hash (HSET)` | $30\text{ days}$ | Enriched fraud decision metadata |

---

## 7. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant Enforced |
| :--- | :--- | :--- |
| Concurrent Duplicate Operation | Lua script detects `fraud:op:{operationId}` exists $\rightarrow$ returns `{0, "REPLAY_DETECTED"}` | `I-DF-004` |
| Risk Score Exceeds Threshold | Lua script sets `user:{userId}:blocked = "1"` $\rightarrow$ subsequent transfers blocked | `I-ACCOUNT-001`, `I-DF-002` |
| Dragonfly Temporary Disconnect | Lettuce driver initiates auto-reconnect; synchronous commands throw clear transient exception | `I-DF-003` |
| Multi-key script without pre-declared keys | N/A (Scripts verified to declare all `KEYS[1..N]` explicitly) | `I-DF-002` |

---

## 8. Acceptance Criteria

- [x] `docker-compose.yaml` configured with DragonflyDB (`docker.dragonflydb.io/dragonflydb/dragonfly:v1.28.0`) with UDS and TCP support.
- [x] `IntegrationTestBase.java` upgraded to spin up DragonflyDB for Testcontainers integration tests.
- [x] All existing fraud rules (`UserBlockRule`, `GlobalVelocityRule`, `SlidingWindowRule`, `NewRecipientRule`) pass 100% of unit and integration tests.
- [x] Dedicated test suite `DragonflyLuaCompatibilityIT` verifies `REVIEW_COUNT_PROTECTED_SCRIPT` and `BLOCK_PROTECTED_SCRIPT` concurrency and replay protection.
- [x] `AccountBlockingIT`, `FraudIT`, and `FraudReactionIT` pass green with DragonflyDB.

