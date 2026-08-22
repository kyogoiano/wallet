# 📐 Architecture Plan: PLAN-000 — Architecture Alignment & Modulith Core Baseline

- **Associated Spec**: [`SPEC-000-architecture-alignment-modulith-baseline.md`](file:///.spec/SPEC-000-architecture-alignment-modulith-baseline.md)
- **Status**: Approved
- **Date**: 2026-08-22
- **Author**: Antigravity Financial Architecture Team

---

## 1. Technical Strategy & Architecture Overview

The purpose of **`PLAN-000`** is to restructure the existing codebase into a verified **Spring Modulith baseline** with explicit module boundaries before building new business capabilities.

### Architectural Blueprint

```mermaid
flowchart TD
    subgraph Modulith ["Wallet Application (Spring Modulith Runtime)"]
        subgraph InfrastructureModule ["infrastructure Module (External Adapters)"]
            RestControllers["REST Controllers (infrastructure.rest)"]
            NatsConsumers["NATS Consumers (infrastructure.messaging)"]
            AppConfig["Configuration (infrastructure.config)"]
        end

        subgraph CoreModule ["core Module (Transactional Ledger)"]
            subgraph CoreApi ["core.api (Published Interface)"]
                TransferFundsAPI["TransferFunds Use Case"]
                DepositFundsAPI["DepositFunds Use Case"]
                WithdrawFundsAPI["WithdrawFunds Use Case"]
                GetBalanceAPI["GetBalance Query"]
                WalletEvents["WalletEvents (Domain Events)"]
            end

            subgraph CoreInternal ["core.internal (Sealed Implementation)"]
                CoreServices["Use Case Services (TransferFundsService, etc.)"]
                FraudGuard["FraudCheckHelper (Integration Gate)"]
                CoreDomain["Domain Entities (Account, LedgerEntry, HashUtil)"]
                Persistence["DAOs (AccountDao, LedgerDao, OutboxDao)"]
                OutboxRelay["Outbox Relay & JetStream Publisher"]
                
                CoreServices --> FraudGuard
                CoreServices --> CoreDomain
                CoreServices --> Persistence
                Persistence --> OutboxRelay
            end

            CoreApi --- CoreServices
        end

        RestControllers -->|invokes| CoreApi
        NatsConsumers -->|invokes| CoreApi
    end

    subgraph Subprojects ["Gradle Subprojects"]
        CoreLib[":core (Tracing Aspect & Context)"]
        FraudEngine[":fraud (Anti-Fraud & Risk Engine)"]
    end

    CoreModule --> CoreLib
    FraudGuard --> FraudEngine
    InfrastructureModule --> CoreLib
```

---

## 2. Spring Modulith Dependency Configuration

We update the root `build.gradle` to import the **Spring Modulith BOM** and starters:

```groovy
ext {
    springModulithVersion = "2.1.0" // Aligned with Spring Boot 4.x
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.modulith:spring-modulith-bom:${springModulithVersion}"
    }
}

dependencies {
    // Spring Modulith Runtime & Test
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    testImplementation 'org.springframework.modulith:spring-modulith-starter-test'
    testImplementation 'org.springframework.modulith:spring-modulith-docs'
}
```

---

## 3. Detailed Package Migration Mapping

| Current Location | Target Location | Modulith Visibility | Responsibility |
| :--- | :--- | :--- | :--- |
| `br.com.wallet.application.usecase.*` | `br.com.wallet.core.api.*` | **PUBLIC (API)** | Published Use Case contracts (`TransferFunds`, `DepositFunds`, etc.) |
| `br.com.wallet.domain.event.*` | `br.com.wallet.core.api.event.*` | **PUBLIC (API)** | Published Domain Events (`MoneyReceivedEvent`, `MoneySentEvent`) |
| `br.com.wallet.domain.AccountBalance` | `br.com.wallet.core.api.dto.AccountBalance` | **PUBLIC (API)** | Balance snapshot projection model |
| `br.com.wallet.domain.LedgerValidationResult` | `br.com.wallet.core.api.dto.LedgerValidationResult` | **PUBLIC (API)** | Ledger integrity result model |
| `br.com.wallet.application.service.*` | `br.com.wallet.core.internal.service.*` | **PACKAGE-PRIVATE (Internal)** | Use Case implementations with `@Transactional` boundaries |
| `br.com.wallet.domain.Account`, `LedgerEntry` | `br.com.wallet.core.internal.domain.*` | **PACKAGE-PRIVATE (Internal)** | Core entities, SHA-256 hash chaining |
| `br.com.wallet.util.HashUtil` | `br.com.wallet.core.internal.domain.HashUtil` | **PACKAGE-PRIVATE (Internal)** | Deterministic cryptographic hashing |
| `br.com.wallet.application.fraud.*` | `br.com.wallet.core.internal.guard.*` | **PACKAGE-PRIVATE (Internal)** | Pre-execution fraud check coordination with `:fraud` |
| `br.com.wallet.infrasctructure.persistence.*` | `br.com.wallet.core.internal.persistence.*` | **PACKAGE-PRIVATE (Internal)** | JDBC DAOs with row-level `SELECT FOR UPDATE` |
| `br.com.wallet.infrasctructure.outbox.*` | `br.com.wallet.core.internal.outbox.*` | **PACKAGE-PRIVATE (Internal)** | Transactional Outbox persistence and publishing |
| `br.com.wallet.interfaces.rest.*` | `br.com.wallet.infrastructure.rest.*` | **INTERNAL (Adapter)** | REST controllers, OpenAPI specs, DTOs, mappers |
| `br.com.wallet.infrasctructure.messaging.*` | `br.com.wallet.infrastructure.messaging.*` | **INTERNAL (Adapter)** | NATS JetStream command workers and DLQ consumers |
| `br.com.wallet.config.*` | `br.com.wallet.infrastructure.config.*` | **INTERNAL (Adapter)** | Spring configuration beans (OTel, Redis, NATS) |

---

## 4. Automated Architecture Verification Test

We implement `ModulithArchitectureTest` to enforce module encapsulation on every build:

```java
package br.com.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

@DisplayName("Spring Modulith Architecture Verification")
class ModulithArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(WalletApplication.class);

    @Test
    @DisplayName("Verify that all module boundaries and package encapulations are strictly respected")
    void verifyArchitecture() {
        modules.verify();
    }

    @Test
    @DisplayName("Generate PlantUML component diagrams and documentation")
    void generateDocumentation() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualFilesAsPlantUml();
    }
}
```

---

## 5. Architectural Decision Records (ADRs)

### 🏛️ ADR-000-1: Spring Modulith for In-Process Modular Architecture
- **Context**: We need modularity for future capabilities (Smart Savings, Goal Engine, AI Copilot) without dynamic JAR or OSGi runtime complexity.
- **Decision**: Adopt Spring Modulith 2.1.x as the architectural framework for in-process application modules and structural verification.
- **Consequences**:
  - Positive: Clean `core.api` vs `core.internal` separation, verified by unit tests in $<2\text{s}$.
  - Positive: Ready-to-use `@ApplicationModuleListener` for internal event routing.
  - Neutral: Requires clean package organization under root package `br.com.wallet`.

### 🏛️ ADR-000-2: Reorganization of Root Packages
- **Context**: The root project had top-level packages (`application`, `domain`, `infrasctructure`, `interfaces`) which flattened all concerns.
- **Decision**: Restructure into two top-level modules under `br.com.wallet`:
  1. `core`: containing `core.api` and `core.internal`.
  2. `infrastructure`: containing `rest`, `messaging`, and `config`.
  Fix the typo `infrasctructure` $\rightarrow$ `infrastructure`.
- **Consequences**:
  - Positive: Clear dependency direction: `infrastructure` $\rightarrow$ `core.api`.
  - Positive: Spring Modulith easily detects `core` and `infrastructure` as distinct modules.

---

## 6. Verification and Regression Prevention Strategy

1. **Step-by-Step Migration**:
   - Update `build.gradle` with Modulith dependencies.
   - Refactor packages and update import statements systematically.
   - Run `ModulithArchitectureTest` to assert clean verification.
   - Run existing unit tests (`TransferFundsServiceTest`, `LedgerValidationServiceTest`, `SlidingWindowRuleTest`).
   - Run existing integration tests (`TransferFundsScenarioTest`, `LedgerScenarioTest`, `OutboxPublisherTest`).
2. **Zero Invariant Deviation**:
   - Hash calculations, SQL table structures, and fraud evaluation points remain strictly identical.
