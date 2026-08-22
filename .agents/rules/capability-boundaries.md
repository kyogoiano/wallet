# 🛡️ Capability & Module Boundary Rules

These rules govern the development of all wallet capabilities, application modules, extensions, and AI tools. They are non-negotiable architectural constraints enforced at compile-time and runtime via **Spring Modulith**.

---

## Architectural Mantra

> **"Capabilities observe, analyze, decide, and propose. Wallet Core authorizes and executes."**

---

## Modulith & Boundary Rules

- **`RULE-CAP-001` (Core Internal Access Prohibition)**: Application modules (`savings`, `goals`, `intelligence`, `copilot`) MUST NOT import or reference `br.com.wallet.core.internal.*` classes, DAOs, or database repositories. They MUST only interact with `br.com.wallet.core.api.*`.
- **`RULE-CAP-002` (Ledger & Projection Mutation Prohibition)**: An application module MUST NOT directly mutate ledger, balance, account, or outbox state.
- **`RULE-CAP-003` (Typed Core API Invocation)**: All monetary state transitions and side effects MUST be executed by calling typed use case interfaces exposed under `br.com.wallet.core.api`.
- **`RULE-CAP-004` (In-Process Modulith Events)**: Application modules observe core events using `@ApplicationModuleListener`. Event handling MUST tolerate at-least-once delivery and remain non-blocking to the core transaction.
- **`RULE-CAP-005` (Mandatory Idempotency Key)**: All commands dispatched to `core.api` MUST carry an explicit `operation_id` derived deterministically from the root trigger event or proposal.
- **`RULE-CAP-006` (Automated Architecture Verification)**: The test suite MUST execute `ApplicationModules.of(WalletApplication.class).verify()`. Any PR or commit that introduces forbidden module coupling MUST fail the build.
- **`RULE-CAP-007` (Transport Agnosticism)**: Capabilities MUST remain completely decoupled from their transport implementations (HTTP, NATS, MCP, SSE). External controllers/tools adapt requests into domain module calls.
