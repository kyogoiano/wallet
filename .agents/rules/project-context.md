---
name: project-context
description: Architecture overview, module boundaries, technology stack, and runtime topology for Wallet Service.
trigger: always_on
---

# 🌐 Wallet Service — Project Context

## 1. System Architecture

The Wallet Service follows **Clean Architecture** and **Domain-Driven Design (DDD)** principles:

```
[ Interfaces / REST Controllers ] (src/main/java/.../interfaces/rest)
                ↓
[ Application / Use Cases ]       (src/main/java/.../application/usecase)
                ↓
[ Core Domain & Rules ]           (core/src/main/java/... & fraud/src/main/java/...)
                ↓
[ Infrastructure & Persistence ]  (src/main/java/.../infrastructure)
```

### Module Structure
- **`:core`**: Pure domain abstractions, exceptions, telemetry annotations, and common context models. Zero framework dependencies.
- **`:fraud`**: Anti-fraud & risk scoring engine, sliding windows, rules (`UserBlockRule`, `GlobalVelocityRule`), and state stores (Caffeine + Redis).
- **Root (`:`)**: Spring Boot application, REST controllers, PostgreSQL JDBC/DAO persistence, Outbox Relay, NATS JetStream integration, and OpenTelemetry OTLP exporter.

---

## 2. Technology Stack

- **Runtime & Language**: Java 26, Spring Boot 4.1.0, Gradle 9.7.1
- **Database**: PostgreSQL 17/19 with schema migrations in `docker/init/schema.sql`
- **Cache & Distributed State**: Redis (Lettuce client with RESP3, Epoll Unix Domain Sockets & TCP fallback)
- **Messaging & Event Streaming**: NATS JetStream (`events.*`, `commands.*`, `commands.dlq.*`)
- **Observability**: OpenTelemetry Java SDK, OpenObserve OTLP backend (Traces, Metrics, Logs)
- **Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL, Redis, NATS)

---

## 3. Data & Storage Model

1. **`accounts`**: Fast projection table holding current balances. Updated synchronously with `SELECT FOR UPDATE` locking.
2. **`ledger`**: Append-only tamper-evident log containing cryptographic SHA-256 hash chains.
3. **`outbox`**: Transactional outbox table storing serialized domain events for asynchronous relay.
4. **Redis**: High-speed ephemeral state, velocity counters, Lua scripts for atomic risk updates, and blocklist caches.

---

## 4. Key Request Flows

- **Write Path (Transfer/Deposit/Withdraw)**:
  1. API Controller receives request with `Idempotency-Key` / `operation_id`.
  2. Fraud Engine evaluates $O(1)$ local Caffeine window & Redis global velocity/blocklist.
  3. Domain Use Case locks accounts in deterministic order (`SELECT FOR UPDATE`).
  4. Balance checked $\rightarrow$ account balances updated $\rightarrow$ ledger entry inserted with next hash $\rightarrow$ outbox event recorded.
  5. Transaction commits atomically.
  6. Outbox Relay scans pending outbox events every 10s and publishes to NATS JetStream with exponential backoff retries.
