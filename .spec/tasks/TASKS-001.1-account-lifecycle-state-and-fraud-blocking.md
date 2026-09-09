# 📝 Task Breakdown: TASKS-001.1 — Account Lifecycle State & Persistent Fraud Blocking Engine

- **Associated Spec**: [`../SPEC-001.1-account-lifecycle-state-and-fraud-blocking.md`](file:///.spec/SPEC-001.1-account-lifecycle-state-and-fraud-blocking.md)
- **Associated Plan**: [`../plans/PLAN-001.1-account-lifecycle-state-and-fraud-blocking.md`](file:///.spec/plans/PLAN-001.1-account-lifecycle-state-and-fraud-blocking.md)
- **Status**: Completed / Verified
- **Author**: Antigravity Financial Architecture Team
- **Date**: 2026-08-23

---

## 1. Traceability Matrix

| Requirement / Invariant ID | Planned Verification Test | Task IDs |
| :--- | :--- | :--- |
| `REQ-ACC-001` | `AccountDaoTest`, `AccountBlockingIT` | `TASK-1.1.1`, `TASK-1.1.3` |
| `REQ-ACC-002` | `AccountBlockedExceptionTest`, `TransferFundsServiceTest` | `TASK-1.1.2`, `TASK-1.1.5` |
| `REQ-ACC-003` | `AccountDaoTest`, `TransferFundsServiceTest`, `DepositFundsServiceTest` | `TASK-1.1.4`, `TASK-1.1.5` |
| `REQ-ACC-004` | `FraudCheckHelperTest`, `FraudReactionIT` | `TASK-1.1.6`, `TASK-1.1.9` |
| `REQ-ACC-005` | `RedisUserStoreTest` | `TASK-1.1.7` |
| `REQ-ACC-006` | `AccountStateServiceTest` | `TASK-1.1.8` |
| `I-ACCOUNT-001` | `AccountDaoTest`, `TransferFundsServiceTest`, `AccountBlockingIT` | `TASK-1.1.4`, `TASK-1.1.5`, `TASK-1.1.9` |
| `I-ACCOUNT-002` | `FraudCheckHelperTest`, `AccountStateServiceTest` | `TASK-1.1.6`, `TASK-1.1.7`, `TASK-1.1.8` |
| `I-ACCOUNT-003` | `AccountDaoTest`, `AccountBlockingIT` | `TASK-1.1.4`, `TASK-1.1.8` |
| `I-ACCOUNT-004` | `RedisUserStoreTest` | `TASK-1.1.7` |

---

## 2. Implementation Tasks (Phased TDD Order)

### Phase 1: Database DDL & Core Foundation
- [x] `TASK-1.1.1` Schema Migration:
  - Add `status`, `blocked_at`, `blocked_reason` to `accounts` table in `../../docker/init/schema.sql`.
- [x] `TASK-1.1.2` Implement `AccountBlockedException`:
  - Create `br.com.wallet.core.exceptions.AccountBlockedException`.

### Phase 2: Domain Models & Enum
- [x] `TASK-1.1.3` Create `AccountStatus` and update Domain Records:
  - Create `AccountStatus` (`ACTIVE`, `BLOCKED`, `SUSPENDED`, `FROZEN`) in `br.com.wallet.ledger.api.domain`.
  - Update `Account` and `AccountBalance` records to include `AccountStatus`.

### Phase 3: DAO Enforcement & Pre-Execution Gates
- [x] `TASK-1.1.4` Update `AccountDao`:
  - Add `blockAccount(UUID walletId, String reason)`, `unblockAccount(UUID walletId)`, `findAccountStatus(UUID walletId)`.
  - Update `getBalancesFromWallets` and `findWalletBalanceForUpdate` to check `status` and throw `AccountBlockedException` if non-active (`I-ACCOUNT-001`).
- [x] `TASK-1.1.5` Update Service Tests & Verifications:
  - Verify `TransferFundsServiceTest`, `DepositFundsServiceTest`, and `WithdrawFundsServiceTest` handle blocked accounts.

### Phase 4: Fraud Engine Reaction & Dual-Store Sync
- [x] `TASK-1.1.6` Update `FraudCheckHelper`:
  - When `FraudDecision.BLOCK` is returned, update `accounts.status = 'BLOCKED'` in PostgreSQL and sync Redis before throwing `FraudBlockedException`.
- [x] `TASK-1.1.7` Update `RedisUserStore`:
  - Implement `setBlocked(UUID userId, boolean blocked)` for cache invalidation/update.

### Phase 5: Administrative State Management
- [x] `TASK-1.1.8` Implement `AccountStateUseCase` & `AccountStateService`:
  - Expose `blockAccount`, `unblockAccount`, `getAccountStatus` in `ledger.api`.
  - Implement `AccountStateService` and unit test suite.

### Phase 6: Integration Tests & Convergence
- [x] `TASK-1.1.9` Author Integration Tests:
  - `AccountBlockingIT`: Verify that blocked accounts reject transfers, deposits, and capability sweeps.
  - `FraudReactionIT`: Verify that a fraud-blocked transaction marks the account `BLOCKED` persistently in PostgreSQL.
- [x] `TASK-1.1.10` Convergence & Summary:
  - Run full test suite (`./gradlew test`).
  - Author `../summaries/SUMMARY-001.1-account-lifecycle-state-and-fraud-blocking.md`.
