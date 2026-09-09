# ⚖️ Separating Product Intent from Technical Intent

A core pathology of failed software architecture is conflating **Product Intent** (the business requirement, user outcome, and functional invariant) with **Technical Intent** (the architectural topology, storage mechanism, and internal wiring).

When product intent is mixed with technical intent:
1. Architectural changes break business specifications.
2. Business requirements dictate suboptimal technical implementations.
3. Tests assert transient technical plumbing rather than enduring business invariants.

---

## 🔍 The Bifurcated Separation Model

| Dimension | Product Intent (`SPEC-XXX.md`) | Technical Intent (`plans/PLAN-XXX.md`) |
| :--- | :--- | :--- |
| **Primary Question** | **WHAT** does the user/system need to achieve? | **HOW** does the system safely, scalably execute it? |
| **Audience** | Product Owner, Domain Expert, Lead Engineer | Systems Architect, Infrastructure Engineer, TDD Implementer |
| **Domain Scope** | User journeys, business policies, monetary invariants | Modulith boundaries, data schemas, locking, Lua scripts |
| **Stability** | High stability (financial rules rarely change arbitrarily) | Evolutionary (caches, DB engines, versions can upgrade) |
| **Acceptance Criteria** | State transitions, HTTP responses, rejection errors | Modulith check passes, DB query plans, SLAs ($P99 < 2\text{ms}$) |

---

## 🚫 Negative Example: Conflated Specification

```markdown
### REQ-SAV-001 (BAD): Round-up sweep
When a user transfers money, the TransferController should call SavingsExecutionService.
The service must run a SELECT * FROM savings_plans WHERE user_id = :id in Postgres.
Then calculate the difference in Java using Math.ceil(amount), open a new transaction,
and call AccountDao.updateBalance() to deduct funds and insert into the savings table.
```

**Why this fails**:
- Dictates internal classes (`TransferController`, `SavingsExecutionService`, `AccountDao`).
- Violates Modulith boundaries (encourages direct `AccountDao` mutation).
- Leaks database queries and floating point math (`Math.ceil`).

---

## ✅ Positive Example: Separated Clean Specification

### 1. Product Intent (`SPEC-001.md`)
```markdown
### REQ-SAV-001 [MUST]: Round-Up Micro-Savings Execution
- **Trigger**: Occurrence of a completed outward transfer or payment for an active account.
- **Behavior**: The system SHALL compute the difference between the transaction amount and the next integer currency unit (ceiling).
- **Invariant (I-SAV-001)**: Sweep Delta $\Delta = \lceil A \rceil - A$, where $0 \le \Delta < 1.00$. If $\Delta = 0.00$, no sweep occurs.
- **Outcome**: A separate transfer operation of value $\Delta$ is dispatched to the user's configured target savings wallet.
- **Failure Mode**: If participating account status is not ACTIVE, reject with AccountBlockedException.
```

### 2. Technical Intent (`plans/PLAN-001.md`)
```markdown
### ADR-SAV-001: In-Process Event Observation via Spring Modulith
- **Decision**: Observe TransferCompletedEvent using @ApplicationModuleListener.
- **Boundary**: Module br.com.wallet.savings SHALL NOT import br.com.wallet.ledger.internal.*.
- **Execution**: Dispatch sweep command via public TransferFundsUseCase under br.com.wallet.ledger.api.
- **Idempotency**: Generate operation_id deterministically using UUID.nameUUIDFromBytes("ROUNDUP:" + event.operationId()).
- **Persistence**: Store execution audit records in savings_executions table.
```
