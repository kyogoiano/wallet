---
name: capability-driven-development
description: Procedures, boundary rules, lifecycle management, and testing patterns for building modular Wallet Capabilities using Spring Modulith.
---

# 🧩 Capability-Driven Development (CDD) Skill

## 1. Identity & Architectural Mantra

> **"Capabilities observe, analyze, decide, and propose. Wallet Core authorizes and executes."**

This skill governs how new capabilities (`savings`, `goals`, `intelligence`, `copilot`) are designed, implemented, and verified within the Wallet Service using **Spring Modulith Application Modules**.

---

## 2. Capability Taxonomy

- **Capability**: Business domain functionality (e.g. `SmartSavingsCapability`).
- **Application Module**: In-process Spring Modulith module package (e.g. `br.com.wallet.savings`).
- **Tool**: Interaction interface exposing capabilities to external actors (e.g. MCP Tool, REST endpoint).

---

## 3. Capability 5-Fold Action Lifecycle

When implementing capability logic, map every action to one of the 5 categories:

1. **`QUERY`**: Read-only state inspection.
2. **`ANALYZE`**: Feature extraction & pattern recognition from events without state mutation.
3. **`DECIDE`**: Deterministic rule evaluation producing business decisions.
4. **`PROPOSE`**: Creation of a `FinancialProposal` awaiting human/policy approval.
5. **`COMMAND`**: Dispatching a typed, idempotent execution request via `core.api`.

---

## 4. Module & Boundary Rules

### ❌ Strictly Forbidden Patterns (Fails Modulith Verification)
```java
// VIOLATION: Accessing ledger.internal from an application module
import br.com.wallet.ledger.internal.persistence.AccountDao;

@Service
public class BadSavingsService {
    @Autowired
    private AccountDao accountDao; // FORBIDDEN!
}
```

### ✅ Standard Compliant Pattern
```java
// COMPLIANT: Calling ledger.api interface
package br.com.wallet.savings;

import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.core.context.OperationOrigin;

@Service
public class SavingsService {
    private final TransferFundsUseCase transferFundsUseCase;

    public SavingsService(TransferFundsUseCase transferFundsUseCase) {
        this.transferFundsUseCase = transferFundsUseCase;
    }

    public void applySavingsSweep(UUID from, UUID toSavings, BigDecimal amount, UUID opId) {
        transferFundsUseCase.handle(new Transfer(opId, from, toSavings, amount, OperationOrigin.SAVINGS_AUTOMATION));
    }
}
```

---

## 5. Event Observation Pattern

```java
package br.com.wallet.savings.internal.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import br.com.wallet.ledger.api.event.DepositCompletedEvent;
import br.com.wallet.core.context.OperationOrigin;

@Component
public class SavingsEventListener {

    private final SavingsExecutionService savingsExecutionService;

    public SavingsEventListener(SavingsExecutionService savingsExecutionService) {
        this.savingsExecutionService = savingsExecutionService;
    }

    @ApplicationModuleListener
    public void onDeposit(DepositCompletedEvent event) {
        if (event.origin() != OperationOrigin.USER) return;
        savingsExecutionService.processDeposit(event);
    }
}
```

---

## 6. TDD Verification Checklist for Capabilities

1. **Architecture Verification**: Ensure `ModulithArchitectureTest` passes with `ApplicationModules.of(WalletApplication.class).verify()`.
2. **Module Test**: Test the module in isolation with `@ApplicationModuleTest`.
3. **Isolation Test**: Simulate an uncaught exception in the module's `@ApplicationModuleListener` and verify that the core transaction remains uncorrupted.
4. **Idempotency Test**: Fire duplicate events with the same `operationId` and assert that duplicate commands are never executed.
5. **Proposal Test**: If generating proposals, assert that unapproved proposals cannot trigger financial execution.
