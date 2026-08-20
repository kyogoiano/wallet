---
name: outbox-messaging
description: Transactional Outbox Pattern, Outbox Relay, NATS JetStream integration, retry exponential backoff, and DLQ handling.
---

# 📦 Outbox Messaging & Event Relay Skill

## 1. Identity & Objective

This skill guides the implementation and verification of the **Transactional Outbox Pattern** and **NATS JetStream** event publishing in Wallet Service.

---

## 2. Core Architecture

```mermaid
flowchart LR
    subgraph Atomic DB Transaction
        Ledger[(Ledger)]
        Accounts[(Accounts)]
        Outbox[(Outbox Table)]
    end

    Outbox -->|Polling / SKIP LOCKED| Relay[OutboxRelay (10s)]
    Relay -->|Deduplicated Publish| NATS[NATS JetStream (events.*)]
    Relay -->|Exhausted Retries| DLQ[(Dead Letter Queue / Status DEAD)]
```

---

## 3. The Outbox Table Schema

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `UUID` | Unique outbox event identifier |
| `event_type` | `VARCHAR(50)` | E.g., `TRANSFER_COMPLETED`, `DEPOSIT_COMPLETED` |
| `payload` | `TEXT / JSON` | Serialized domain event payload |
| `status` | `VARCHAR(20)` | `PENDING`, `PROCESSED`, `FAILED`, `DEAD` |
| `retry_count` | `INTEGER` | Number of failed attempts |
| `next_retry_at`| `TIMESTAMP` | Timestamp for next allowable retry |
| `created_at` | `TIMESTAMP` | Record creation timestamp |
| `processed_at`| `TIMESTAMP` | Completion timestamp |

---

## 4. Relay Processing & Retry Rules

1. **Claiming Batch**: `OutboxDao.claimBatch(now, 100)` uses PostgreSQL `FOR UPDATE SKIP LOCKED` to allow multiple parallel relay workers without race conditions.
2. **NATS Deduplication**: Uses `Nats-Msg-Id: operationId` header in NATS JetStream messages to ensure idempotent delivery.
3. **Exponential Backoff**: When publishing fails:
   $$\text{backoff} = 2^{\text{retry\_count}} \text{ seconds}$$
4. **Dead Letter Handling**: If `retry_count > 10`, mark event as `DEAD` for manual operational inspection.

---

## 5. Event Publishing Contracts

- Production: Uses `NatsEventPublisher` (active under `!test & !in-memory`).
- Integration Testing: Uses `InMemoryEventPublisher` or `FailingEventPublisher` (active under `test` / `in-memory`).
