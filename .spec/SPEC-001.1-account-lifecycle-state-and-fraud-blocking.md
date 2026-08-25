# 📋 Specification: SPEC-001.1 — Account Lifecycle State & Persistent Fraud Blocking Engine

- **Status**: Ratified / Approved (Gate 1 Passed)
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-23
- **Target Release / Milestone**: Wallet Service V4 — Phase 1.1 (Resilience & Fraud Integrity Patch)
- **Architectural Mantra**: *"Capabilities observe, analyze, and decide. The Financial Core authorizes, guards account lifecycle, and executes."*

---

## 1. Intent & Business Value

To eliminate the architectural gap between transient fraud detection and persistent account state. 

Previously, when the **Fraud & Risk Engine** (`:fraud`) issued a `FraudDecision.BLOCK`, the single transaction was aborted, but the underlying account in PostgreSQL remained `ACTIVE`. This allowed malicious actors to retry once velocity windows expired.

**`SPEC-001.1`** establishes:
1. **Persistent Account Lifecycle State** (`accounts.status`: `ACTIVE`, `BLOCKED`, `SUSPENDED`, `FROZEN`) in PostgreSQL.
2. **Standardized Domain Exception** (`AccountBlockedException` in `core.exceptions`).
3. **Pre-Execution Account Gate** in the Financial Core (`ledger`) rejecting any debit, credit, transfer, or automated capability sweep on non-`ACTIVE` accounts.
4. **Automated Fraud Reaction Loop**: High-risk fraud decisions automatically transition the account to `BLOCKED` in DB and update the Redis distributed cache.
5. **Database-Backed Fallback in Redis Cache**: Replacing the stub in `RedisUserStore.getFallbackValue()`.
6. **Administrative Account State Management** (`AccountStateUseCase`: block, unblock, query status).

```
┌────────────────────────────┐
│   Fraud / Admin Trigger    │
└──────────────┬─────────────┘
               │
               ▼
┌────────────────────────────┐      Dual-Store Sync       ┌────────────────────────────┐
│ PostgreSQL accounts table  │ ─────────────────────────> │ Redis user:{userId}:blocked│
│ (status = 'BLOCKED')       │                            │ (TTL = 10 minutes)         │
└──────────────┬─────────────┘                            └────────────────────────────┘
               │
               ▼
┌────────────────────────────┐
│ Financial Core Gate        │
│ (SELECT FOR UPDATE)        │
│ └── IF status != 'ACTIVE'  │
│     THROW AccountBlocked   │
└────────────────────────────┘
```

---

## 2. Mathematical & System Invariants

- **`I-ACCOUNT-001` (Account State Gate)**:
  Any monetary operation (`Transfer`, `Deposit`, `Withdraw`, `Savings Sweep`) involving a participating account whose status is not `ACTIVE` MUST be rejected with `AccountBlockedException`:
  $$\forall a \in \{\text{sourceAccount}, \text{targetAccount}\}, \quad a.\text{status} \neq \text{ACTIVE} \implies \text{ABORT}(\text{AccountBlockedException})$$
- **`I-ACCOUNT-002` (Dual-Store Consistency & Redis Synchronization)**:
  When an account transitions to `BLOCKED` or `ACTIVE` in PostgreSQL, the distributed cache key `user:{userId}:blocked` in Redis MUST be updated/invalidated:
  $$\text{PostgreSQL}(\text{status} \leftarrow \text{'BLOCKED'}) \implies \text{Redis}(\text{SET } \text{user}:userId:\text{blocked} = 1, \text{TTL} = 10\text{m})$$
  $$\text{PostgreSQL}(\text{status} \leftarrow \text{'ACTIVE'}) \implies \text{Redis}(\text{DEL } \text{user}:userId:\text{blocked})$$
- **`I-ACCOUNT-003` (Auditability of Blocks)**:
  Every blocking event in PostgreSQL MUST record the timestamp (`blocked_at`) and textual reason (`blocked_reason`).
- **`I-ACCOUNT-004` (Fail-Closed User Store Fallback)**:
  If the Redis cache is unavailable or experiences a cache miss, `RedisUserStore` MUST query the authoritative PostgreSQL `accounts` table instead of returning a hardcoded unblocked state.

---

## 3. Functional Requirements

- **`REQ-ACC-001` (Account Status Column & Enum)**: The `accounts` table SHALL include `status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'`, `blocked_at TIMESTAMPTZ`, and `blocked_reason TEXT`. Status enum values: `ACTIVE`, `BLOCKED`, `SUSPENDED`, `FROZEN`.
- **`REQ-ACC-002` (Core Exception)**: The system SHALL define `AccountBlockedException` in package `br.com.wallet.core.exceptions`.
- **`REQ-ACC-003` (Pre-Execution Transaction Gate)**: During account retrieval for update (`AccountDao.getBalancesFromWallets`, `findWalletBalanceForUpdate`), the system SHALL verify account status and throw `AccountBlockedException` if not `ACTIVE`.
- **`REQ-ACC-004` (Automated Fraud Blocking)**: When `FraudCheckHelper` receives `FraudDecision.BLOCK` (or risk score $\ge 70$), it SHALL update `accounts.status = 'BLOCKED'` with `blocked_at = NOW()` and `blocked_reason`, synchronize Redis, emit `FraudEvent`, and throw `FraudBlockedException`.
- **`REQ-ACC-005` (Redis Fallback to Database)**: `RedisUserStore.getFallbackValue(userId)` SHALL query the database via a dedicated user status repository/dao to resolve authoritative user block status.
- **`REQ-ACC-006` (Administrative Block & Unblock API)**: The system SHALL expose `AccountStateUseCase` in `br.com.wallet.ledger.api` allowing administrators to block and unblock accounts with audit logging.

---

## 4. Non-Functional Requirements

- **Performance**: Account status verification SHALL execute in $O(1)$ during existing `SELECT FOR UPDATE` queries with zero additional DB round-trips.
- **Modularity**: Zero cyclic dependencies across Modulith modules (`core`, `fraud`, `ledger`, `savings`, `infrastructure`).
- **Resilience**: Redis outages SHALL fallback gracefully to PostgreSQL without falsely allowing blocked users.

---

## 5. Interface Contracts

### 5.1 Public Domain Model (`br.com.wallet.ledger.api.domain`)

```java
package br.com.wallet.ledger.api.domain;

public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    SUSPENDED,
    FROZEN
}
```

```java
package br.com.wallet.ledger.api.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Account(
        UUID id,
        BigDecimal balance,
        Long version,
        UUID userId,
        AccountStatus status,
        Instant blockedAt,
        String blockedReason,
        Instant createdAt
) {
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
```

### 5.2 Public Exception (`br.com.wallet.core.exceptions`)

```java
package br.com.wallet.core.exceptions;

import java.util.UUID;

public class AccountBlockedException extends RuntimeException {
    private final UUID walletId;
    private final String reason;

    public AccountBlockedException(UUID walletId, String reason) {
        super("Account is blocked. walletId=" + walletId + ", reason=" + reason);
        this.walletId = walletId;
        this.reason = reason;
    }

    public AccountBlockedException(String message) {
        super(message);
        this.walletId = null;
        this.reason = message;
    }

    public UUID getWalletId() { return walletId; }
    public String getReason() { return reason; }
}
```

### 5.3 Public Use Case (`br.com.wallet.ledger.api`)

```java
package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.domain.AccountStatus;
import java.util.UUID;

public interface AccountStateUseCase {
    void blockAccount(UUID walletId, String reason);
    void unblockAccount(UUID walletId);
    AccountStatus getAccountStatus(UUID walletId);
}
```

---

## 6. Failure Modes & Edge Cases

| Scenario | Expected Behavior | Invariant |
| :--- | :--- | :--- |
| Transfer attempted from `BLOCKED` account | Aborted with `AccountBlockedException` before ledger write | `I-ACCOUNT-001` |
| Transfer attempted to `BLOCKED` target account | Aborted with `AccountBlockedException` | `I-ACCOUNT-001` |
| Deposit attempted on `BLOCKED` account | Aborted with `AccountBlockedException` | `I-ACCOUNT-001` |
| Automated savings sweep on `BLOCKED` account | Caught in `SavingsExecutionService`, marked `REJECTED_BY_FRAUD` / `SKIPPED` | `I-SAVINGS-003`, `I-ACCOUNT-001` |
| Redis down during `UserBlockRule` check | Fallback queries PostgreSQL `accounts.status` to confirm user block state | `I-ACCOUNT-004` |
| Account unblocked by Admin | Status set to `ACTIVE` in DB, Redis key deleted, subsequent operations succeed | `I-ACCOUNT-002` |

---

## 7. Acceptance Criteria

- [ ] `accounts` table migration adds `status`, `blocked_at`, `blocked_reason`.
- [ ] `AccountBlockedException` added to `br.com.wallet.core.exceptions`.
- [ ] `Account` and `AccountBalance` models include `AccountStatus`.
- [ ] `AccountDao` checks account status during `SELECT FOR UPDATE` and enforces `I-ACCOUNT-001`.
- [ ] `FraudCheckHelper` updates account status to `BLOCKED` on `FraudDecision.BLOCK`.
- [ ] `AccountStateUseCase` implemented with `blockAccount` and `unblockAccount`.
- [ ] Unit & integration tests verify end-to-end blocking, transfer rejection, and unblocking.
