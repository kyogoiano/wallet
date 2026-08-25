---
name: project-context
description: Architecture overview, module boundaries, technology stack, and runtime topology for Wallet Service.
trigger: always_on
---

# 🌐 Wallet Service — Project Context

## 1. System Architecture (Spring Modulith DAG)

The Wallet Service follows **Modular Monolith (Spring Modulith)** and **Clean Architecture / DDD** principles:

```
┌────────────────────────────────────────────────────────┐
│             br.com.wallet.infrastructure               │
│    (REST Controllers, NATS JetStream, Config, DLQ)     │
└──────────────┬───────────┬───────────────┬─────────────┘
               │           │               │ depends on
               ▼           ▼               ▼
┌────────────────────────────┐    ┌──────────────────────┐
│    br.com.wallet.ledger    │───>│ br.com.wallet.fraud  │
│ (Use Cases, Ledger, Outbox)│    │(Engine, Rules, State)│
└──────────────┬─────────────┘    └──────────┬───────────┘
               │ depends on                  │ depends on
               └───────────────┬─────────────┘
                               ▼
                ┌────────────────────────────┐
                │    br.com.wallet.core      │
                │(TraceContext, FraudContext)│
                └────────────────────────────┘
```

### Module Structure
- **`:core` (`br.com.wallet.core`)**: Shared foundational types: `TraceContext`, `FraudContext` (implements `TraceContext`), `OperationOrigin`, `Traceable`, `TracingAspect`, `IdempotencyException`, `AccountBlockedException`. Zero outgoing dependencies.
- **`:fraud` (`br.com.wallet.fraud`)**: Anti-fraud & risk scoring engine, sliding windows, rules (`UserBlockRule`, `GlobalVelocityRule`), and state stores (Caffeine + Redis).
- **`br.com.wallet.ledger` (in root)**: Transactional ledger & core banking domain (`TransferFundsUseCase`, `DepositFundsUseCase`, `WithdrawFundsUseCase`, `BalanceUseCase`, `ValidateLedgerUseCase`, `AccountStateUseCase`), `AccountDao`, `LedgerDao`, `OutboxDao`, and `FraudCheckHelper`.
- **`br.com.wallet.savings` (in root)**: Smart Savings capability module (`SavingsPlanUseCase`, `SavingsQueryUseCase`, `SavingsRuleEngine`, `SavingsEventListener`) reacting to banking events via `@ApplicationModuleListener`.
- **`br.com.wallet.infrastructure` (in root)**: REST controllers, NATS JetStream workers, DLQ persistence (`DlqOperationsDao`), and Spring configuration.

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
