---
name: coding-standards
description: Coding guidelines, modern Java 27 patterns, clean architecture boundaries, and error handling conventions.
trigger: model_decision
---

# 💻 Coding Standards & Conventions

## 1. Modern Java & Idiomatic Style

- **Java Version**: Target Java 27 language features.
- **Records**: Use `record` for immutable domain events, DTOs, value objects, and context carriers.
- **Sealed Interfaces & Pattern Matching**: Use sealed types and pattern matching for domain results (e.g., `VelocityResult.Ok`, `VelocityResult.Exceeded`, `VelocityResult.Replay`).
- **Null Safety**: Annotate non-nullable parameters and return types with `@NonNull` (from `org.jspecify.annotations.NonNull`).
- **Immutability by Default**: Fields must be `final` wherever possible. Avoid mutable shared state.
- **Zero Lombok**: Do not introduce or use Lombok; use native Java records, explicit constructors, and standard Java idioms.

---

## 2. Clean Architecture & Layering

- **Controllers**: Thin controllers that handle HTTP serialization, header extraction (`Idempotency-Key`, `operation_id`), input validation, and delegate immediately to Use Cases.
- **Use Cases**: Encapsulate business workflows. Use cases should not know about HTTP, database drivers, or messaging protocols.
- **Domain Layer (`:core`)**: Pure business logic and domain exceptions. Must remain framework-agnostic.
- **Persistence & Infra**: Keep SQL queries, Lua scripts, and client configuration in the infrastructure layer.

---

## 3. Financial & Numeric Operations

- **Monetary Values**: Always use `BigDecimal` for currency and amounts. Never use `double` or `float`.
- **Hash Normalization**: In ledger calculations, strip trailing zeros from `BigDecimal` (`amount.stripTrailingZeros().toPlainString()`) to guarantee deterministic hashing across scale variations.
- **Rounding & Precision**: Keep currency precision explicit and consistent.

---

## 4. Concurrency & Performance

- **Virtual Threads**: Application runs with `spring.threads.virtual.enabled: true`. Avoid blocking operations inside pinned synchronized blocks; prefer `ReentrantLock` or standard concurrency utilities.
- **Lock Ordering**: When acquiring multiple row locks (e.g., transfers between Wallet A and Wallet B), sort wallet IDs lexicographically to avoid deadlocks:
  ```java
  UUID firstLock = from.compareTo(to) < 0 ? from : to;
  UUID secondLock = from.compareTo(to) < 0 ? to : from;
  ```
- **Redis & Lua**: Multi-key transactional operations in Redis must be executed via Lua scripts to ensure atomicity.

---

## 5. Error Handling & Observability

- **Explicit Exceptions**: Throw domain-specific exceptions (e.g., `InsufficientBalanceException`, `FraudBlockedException`, `IdempotencyException`).
- **REST Status Mapping**: Map exceptions to clear HTTP status codes using `@RestControllerAdvice` in `ApiExceptionHandler.java`.
- **Trace Context**: Annotate critical service methods with `@Traceable("operation.name")` to generate OpenTelemetry spans with baggage propagation.
