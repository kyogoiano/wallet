# 👁️ Telemetry Security & Data Non-Emission

## 1. Non-Emission First vs Sanitization Filters

Relying solely on string-scrubbing regex filters is inherently fragile:
- Exceptions can dump internal state through `getCause()` or unmasked arguments.
- `toString()` can serialize entire records into diagnostic logs.
- MDC contexts can leak account numbers or operation details.
- Distributed tracing spans can record sensitive HTTP query params or headers.

### Primary Rule: Non-Emission at Source
Financial models, command records, and cryptographic envelopes MUST NOT contain sensitive data in their default serialization formats.

## 2. Telemetry Invariants

- **`I-SEC-012`**: $\text{Telemetry}(\text{request}) \cap \text{SensitiveFields}(\text{request}) = \emptyset$.
- Exceptions thrown during crypto operations must contain only:
  - `operationId`
  - `tenantId`
  - Canonical failure category (`CRYPTOGRAPHIC_TAMPER_DETECTED`, `KEY_MANAGEMENT_FAILURE`, etc.)
- Raw byte arrays, ciphertexts, decrypted payloads, and balances are strictly prohibited from exception messages.
