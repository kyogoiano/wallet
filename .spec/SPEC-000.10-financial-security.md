# 📐 Specification: SPEC-000.10 — Financial Security & Payload Cryptographic Protection

- **Status**: 📝 **Draft / Scratch (Rev. 5 — Aligned with Histories 71-74 & JDK 27 PQC Review)**
- **Author**: Antigravity Platform Security & Financial Systems Guild
- **Date**: 2026-09-29
- **Target Release**: Wallet Service V4 — Phase 000.10
- **Bounded Context**: Spring Modulith `br.com.wallet.security` consumed by `:edge` and `:core` (isolated from `br.com.wallet.ledger`)
- **Governing Skills**: [`financial-cryptographic-security`](file:///.agents/skills/financial-cryptographic-security/SKILL.md), [`perimeter-security`](file:///.agents/skills/perimeter-security/SKILL.md), [`spec-driven-development`](file:///.agents/skills/spec-driven-development/SKILL.md)
- **Line Budget**: Max 250 lines (`I-SDD-006`). Envelope encryption, two-phase replay, KMS binding, Modulith isolation, and JDK 27 PQC runtime.

---

## 0. Pre-Flight History & Context Audit

- **Histories & Specs Audited**:
  - **JDK 27 Security Release (2026-09-15)**: Integrated JEP 527 (Post-Quantum Hybrid TLS 1.3 `X25519MLKEM768`), JEP 538 (PEM API Preview 3), KeyStore `Instant` API (JDK-8374808), and `jcmd VM.security_properties`.
  - [`.histories/history74.txt`](file:///.histories/history74.txt): Mandated two-phase nonce reservation (`reserve`/`commit`/`release`), KMS Encryption Context (`tenant_id`), DEK cache entry semantics (`DekCacheKey`), technology adapters in `infrastructure`, strict envelope validation, and `I-ENV-006` (GCM IV uniqueness).
  - [`.histories/history73.txt`](file:///.histories/history73.txt): Established Modulith contracts vs adapters in infrastructure, immutable `CryptoBytes`, `SensitiveKeyMaterial` zeroization, length-prefixed canonical AAD (`WALLET-ENV-AAD-V1`), benchmark targets, and `financial-cryptographic-security` skill.
  - [`.histories/history72.txt`](file:///.histories/history72.txt) & [`.histories/history71.txt`](file:///.histories/history71.txt): Established decrypt-before-transaction (`I-ENV-003`), permission separation, and telemetry non-emission.
- **Constitutional Invariants**: `I-LEDGER-001` (Immutable ledger), `I-ATOMICITY-001` (Single tx boundary; zero external blocking while holding locks), `I-SEC-001` (No implicit identity), `I-SEC-003` (Zero-DB at Edge).

---

## 1. Intent & Architectural Boundary

`SPEC-000.10` formalizes application-layer payload confidentiality between Edge persistence/transport and Core:
```text
Client --(TLS 1.3 PQC Hybrid)--> Edge [Auth HMAC -> Nonce.reserve() -> DEK Cache -> Encrypt]
                                     │
                                     ├──> Fsync to /spool (TASK-6.6) -> Nonce.commit()
                                     └──> Opaque CryptoEnvelope over NATS JetStream (PQC TLS)
                                             │
                                             ▼
Core [EnvelopeDecryptor.decrypt()] ──(Plaintext)──> BEGIN DB TX [Ledger/Outbox] ──> COMMIT
```
> **Architectural Mantra**: *"Financial Security protects financial data; it does not own financial state. Security owns security contracts; infrastructure owns technology adapters."*

---

## 2. Modulith Topology & Cryptographic Domains

The `br.com.wallet.security` module publishes provider-neutral contracts and pure engines:
- **`envelope`**: `CryptoEnvelope`, `CryptoBytes`, `EnvelopeEncryptor`, `EnvelopeDecryptor`, `EnvelopeCodec`, `CanonicalAad`.
- **`keymanagement`**: `KeyManagementClient` SPI, `SensitiveKeyMaterial`, `GeneratedDataKey`, `KeyContext`.
- **`replay`**: `NonceTracker` SPI, `ReplayKey`, `NonceReservation` (`Reserved`, `Rejected`, `Unavailable`).
- **`telemetry` & `failure`**: `SensitiveFieldPolicy`, `TelemetrySanitizer`, `CryptoEnvelopeSummary`, `CryptographicIntegrityException`, `SecurityFailureCategory`.
*Adapters* (`DragonflyNonceTracker`, `LocalApplianceKeyManagementClient`, `CachedKeyManagementClient`, Cloud KMS) reside strictly in `br.com.wallet.infrastructure`. `InMemoryKeyManagementClient` is test-scoped (`src/test/java`).

**Cryptographic Domain Separation**:
- **`WALLET-ENV-V1`**: Application-layer payload confidentiality & authenticity (AES-256-GCM + AAD).
- **`WALLET-HMAC-V1`**: Ingress perimeter request authentication (SPEC-000.9.3).
- **`WALLET-NONCE-V1`**: Ingress replay protection key namespace (`nonce:{tenantId}:{principalId}:{nonce}`).
- **`WALLET-LEDGER-HASH-V1`**: Cryptographic append-only ledger tampering chain (`I-LEDGER-002`).

---

## 3. Mathematical & System Invariants

- **`I-ENV-001` (Zero Plaintext at Rest & In-Transit)**: Financial payloads in `/spool` or NATS MUST be length-prefixed serialized `CryptoEnvelope` bytes (`TASK-6.6`):
  $$\text{Payload}_{\text{spool}} = \text{AES-GCM-256}(K_{\text{DEK}}, \text{Plaintext}, \text{IV}, \text{AAD}), \quad \text{Plaintext} \cap \text{SpoolDiskBytes} = \emptyset$$
- **`I-ENV-002` (Two-Tier Cryptographic Binding)**:
  - **Tier 1 (KMS)**: $\text{KmsContext} = \{\text{"WALLET-ENV-V1"}, \text{"tenant\_id"}: \text{tenantId}, \text{"key\_domain"}: \text{"WALLET-ENV-V1"}\}$.
  - **Tier 2 (GCM AAD)**: Length-prefixed binary encoding binds `version`, `tenantId`, `operationId`, `keyId`, `algorithm`. Tampering causes GCM tag verification failure.
- **`I-ENV-003` (Decrypt-Before-Transaction)**: Core MUST unwrap DEK and decrypt payload **strictly before** opening the PostgreSQL transaction (`I-ATOMICITY-001`). Zero KMS, cache, or network I/O while holding DB locks:
  $$\text{BeginTx}() \succ \text{EnvelopeDecryptor.decrypt}(\text{CryptoEnvelope})$$
- **`I-ENV-004` (Two-Phase Replay Reservation)**: Validated in Dragonfly **strictly after HMAC authentication**: `reserve()` with 10s lease $\to$ `commit()` with 60s TTL upon durable journal fsync $\to$ `release()` on pre-journal failure to prevent transient errors from becoming permanent 401 rejections.
- **`I-ENV-005` (Benchmark Latency Envelope)**: Target $P99 \le 10\mu\text{s}$ for primitive cipher; target $P99 \le 50\mu\text{s}$ for Edge admission crypto pipeline across 256B, 1KB, 2KB, 8KB, 64KB payloads.
- **`I-ENV-006` (GCM IV Uniqueness & Invocation Budget)**: For every encryption under a given DEK, $(K_{\text{DEK}}, \text{IV})$ MUST NOT repeat. 96-bit CSPRNG IVs via `SecureRandom`; DEK usage capped at 100,000 encryptions.
- **`I-SEC-011` (KMS Air-Gap & Key Isolation)**: Master KEKs never leave KMS. Edge holds `generateDataKey` permission; Core holds `decryptDataKey` permission; Ledger holds neither.
- **`I-SEC-012` (Non-Emission First Telemetry)**: Sensitive financial data MUST NOT enter telemetry at the source. `CryptoEnvelopeSummary` used for structured diagnostics.
- **`I-SEC-013` (Financial Security Encapsulation)**: Pure crypto contracts and engines reside in `br.com.wallet.security`, verified via Spring Modulith.
- **`I-SEC-014` (Ledger Architectural Isolation)**: $\text{Deps}(\text{ledger}) \cap \text{Deps}(\text{security}) = \emptyset$.
- **`I-SEC-015` (Dual Boundary & Provider Isolation)**: Layer 1 Gradle build dependencies + Layer 2 Spring Modulith verification. Technology adapters reside in `infrastructure`.
- **`I-SEC-016` (Cryptographic Value Immutability & Key Zeroization)**: `CryptoBytes` enforces defensive copying on constructor and accessor. `SensitiveKeyMaterial` auto-closes with memory zeroization (`Arrays.fill(material, (byte) 0)`).

---

## 4. Cross-Feature Impact Matrix (`I-SDD-005`)

| Participating Module | Affected Flow / Contract | Potential Side Effect / Failure Mode | Invariant / Mitigation |
| :--- | :--- | :--- | :--- |
| **`:edge` (Ingress)** | Admission & `/spool` journal | Cryptographic latency on hot path | `I-ENV-005` ($P99 \le 50\mu\text{s}$ benchmark target; bounded DEK cache) |
| **`:edge` (Journal)** | `SegmentedFileJournal` flush | Plaintext leakage during crash dumps | `I-ENV-001` (Serialized length-prefixed `CryptoEnvelope` bytes; `TASK-6.6`) |
| **`:core` (Consumer)**| NATS command consumption | Lock contention if crypto blocks DB | `I-ENV-003` (Decrypt strictly before `BEGIN TRANSACTION`) |
| **`ledger`** (Core) | Domain use cases & DAOs | Accidental coupling to encryption | `I-SEC-014` (Zero ledger dependencies on security module) |
| **`infrastructure`** | Dragonfly nonce tracking | Transient failure causes permanent 401 | `I-ENV-004` (Two-phase reservation: `reserve()` $\to$ `commit()` / `release()`) |
| **Observability** | OTel tracing & structured logs | Accidental PII/account leaks in spans | `I-SEC-012` (Non-emission first; `CryptoEnvelopeSummary`) |

---

## 5. Functional Requirements (MoSCoW — `I-SDD-004`)

### 5.1 Pillar A: Envelope Encryption & Spool Protection [MUST]
- **`REQ-SEC-020` [MUST]**: Define immutable `CryptoEnvelope` with structural validation: `version` (`WALLET_ENV_V1`), `tenantId`, `operationId`, `keyId`, `algorithm` (`AES_256_GCM`), `iv` (exactly 12 bytes), `wrappedDek` (non-empty), `ciphertext` ($\ge 16$ bytes).
- **`REQ-SEC-021` [MUST]**: `SegmentedFileJournal` and `BinaryRecordCodec` persist records storing serialized length-prefixed `CryptoEnvelope` bytes (`TASK-6.6`, `I-ENV-001`).
- **`REQ-SEC-022` [MUST]**: Core `CoreCommandConsumer` unwraps DEK and decrypts payload before initiating database transaction (`I-ENV-003`).
- **`REQ-SEC-023` [MUST]**: Two-tier binding: KMS wrapped DEK bound to `KmsEncryptionContext`; payload bound to length-prefixed `CanonicalAad` (`I-ENV-002`).

### 5.2 Pillar B: Key Management Service (KMS) & Lifecycle [MUST]
- **`REQ-SEC-024` [MUST]**: Implement typed `KeyManagementClient` SPI returning `GeneratedDataKey(SensitiveKeyMaterial, CryptoBytes)` and unwrapping to `SensitiveKeyMaterial` in Core.
- **`REQ-SEC-025` [MUST]**: Provide test-scoped `InMemoryKeyManagementClient`, and separate `LocalApplianceKeyManagementClient` (cryptographically random root KEK leveraging JDK 27 `PEMEncoder`/`PEMDecoder`) in `infrastructure`.
- **`REQ-SEC-026` [MUST]**: Bounded Plaintext DEK Cache: Keyed by `(tenantId, keyId)`, max-age 10m, max-uses 100,000 encryptions (`I-ENV-006`), eviction zeroization via `SensitiveKeyMaterial.close()`, unique 12B CSPRNG IV via `SecureRandom`.
- **`REQ-SEC-027` [SHOULD]**: Provide cloud KMS adapters (AWS KMS / GCP KMS / Vault) in `infrastructure` conditionally activated via `@ConditionalOnProperty`.

### 5.3 Pillar C: Post-HMAC Nonce Replay Reservation [MUST]
- **`REQ-SEC-028` [MUST]**: Edge validates `X-Nonce` header *strictly after* HMAC verification via two-phase reservation (`I-ENV-004`). Missing or duplicate nonce returns `HTTP 401 UNAUTHORIZED`.
- **`REQ-SEC-029` [MUST]**: Expose `NonceTracker` SPI in `security.replay` with `reserve()`, `commit()`, `release()` returning `NonceReservation`. Adapter `DragonflyNonceTracker` resides in `infrastructure`.

### 5.4 Pillar D: Telemetry Protection & Failure Taxonomy [MUST]
- **`REQ-SEC-030` [MUST]**: Sensitive financial fields MUST NOT be included in logs, exceptions, or MDC. Expose `CryptoEnvelopeSummary` for structured diagnostics (`I-SEC-012`).
- **`REQ-SEC-031` [MUST]**: Fine-grained Failure Taxonomy: `MalformedEnvelope`, `UnsupportedEnvelopeVersion`, `CryptographicIntegrityFailure` (DLQ), `KeyManagementUnavailable` (retry backoff), `ReplayDetected` (401), `SecurityConfigurationFailure` (503).
- **`REQ-SEC-032` [SHOULD]**: OpenTelemetry span sanitizing processor scrubbing any accidental sensitive attributes.

### 5.5 Pillar E: Modulith Architecture & Dual Isolation [MUST]
- **`REQ-SEC-035` [MUST]**: Pure crypto contracts in `br.com.wallet.security` (`I-SEC-013`). Adapters reside in `infrastructure`.
- **`REQ-SEC-036` [MUST]**: Verify module boundaries with `ApplicationModules.verify()`.
- **`REQ-SEC-037` [MUST]**: Assert zero outgoing dependencies from `br.com.wallet.ledger` to `br.com.wallet.security` (`I-SEC-014`).
- **`REQ-SEC-038` [MUST]**: Expose provider-neutral contracts, isolating concrete crypto and store libraries from Edge and Core (`I-SEC-015`).

### 5.6 Scope Fencing [WON'T]
- **`REQ-SEC-033` [WON'T]**: Full database column encryption for historical ledger rows is deferred.
- **`REQ-SEC-034` [WON'T]**: Asymmetric client-side signatures (mTLS/WebCrypto) remain out of scope for V1.

---

## 6. JDK 27 Cryptographic Runtime Alignment & PQC Review

1. **JEP 527 (Post-Quantum Hybrid TLS 1.3 / `X25519MLKEM768`)**:
   - Ingress Edge connections and NATS TLS connections default to hybrid ML-KEM-768 key exchange, defeating "Harvest Now, Decrypt Later" (HNDL) attacks without application code changes.
2. **Symmetric AES-256 Quantum Resilience**:
   - Grover's algorithm limits quantum speedup to $2^{128}$ operations for 256-bit AES-GCM, ensuring mathematical post-quantum payload safety at rest and in transit.
3. **JEP 538 (Standardized PEM API / `PEMDecoder` / `PEMEncoder`)**:
   - Appliance root KEK and certificate management uses native JDK 27 `PEMEncoder`/`PEMDecoder` without external BouncyCastle dependencies (`I-SEC-015`).
4. **KeyStore `Instant` API (JDK-8374808)**:
   - Eliminates legacy mutable `Date` across all key lifecycle and cache timestamp representations.
5. **Runtime Auditing (`jcmd VM.security_properties`)**:
   - Enables operational verification of `crypto.policy=unlimited` and GCM key limits ($2^{37}$).

---

## 7. Verification Strategy & Test Triads (`I-TDD-002`)

- **Triad 1 (Envelope Encryption, Structural Validation & Immutability)**:
  - *Positive*: Encrypt command with canonical AAD $\to$ write to journal $\to$ read raw segment bytes $\to$ assert zero plaintext occurrences $\to$ decrypt successfully.
  - *Negative*: Tamper 1 bit of ciphertext OR swap metadata $\to$ assert `CryptographicIntegrityFailure`. Mutate input array $\to$ assert envelope unchanged (`CryptoEnvelopeImmutabilityTest`).
  - *Boundary & Substitution*: Attempt cross-tenant envelope or ciphertext substitution $\to$ assert rejection (`CrossTenantEnvelopeSubstitutionTest`, `CryptoEnvelopeSubstitutionTest`). Verify canonical AAD against golden hex vectors (`CanonicalAadGoldenVectorTest`).
- **Triad 2 (Two-Phase Replay Reservation & Recovery)**:
  - *Positive*: Valid HMAC request $\to$ nonce reserved $\to$ journal fsync $\to$ nonce committed. Duplicate retry $\to$ rejected ($401$).
  - *Negative*: Nonce reserved $\to$ simulated KMS or journal failure $\to$ nonce released $\to$ client retry with same nonce succeeds (`NonceAdmissionFailureRecoveryTest`).
  - *Boundary & IV Uniqueness*: 1,000,000 generated IVs under same key yield zero collisions (`GcmIvUniquenessTest`). Storage outage yields `NonceReservation.Unavailable` ($503$).
- **Triad 3 (Decrypt-Before-Transaction & Lock Isolation)**:
  - *Positive*: Valid envelope $\to$ Core unwraps DEK and decrypts payload *prior* to opening DB transaction $\to$ ledger write succeeds.
  - *Negative*: Mock KMS latency (500ms) $\to$ assert zero DB connection pool acquisition and zero row locks held (`I-ENV-003`).
- **Triad 4 (Modulith Architecture, Provider & Ledger Isolation)**:
  - *Positive*: `ApplicationModules.verify()` passes for `br.com.wallet.security`.
  - *Negative*: ArchUnit test asserting zero calls from `br.com.wallet.ledger` into `br.com.wallet.security`.
  - *Boundary*: Dual boundary: technology adapters reside in `infrastructure`; `:edge` and `:core` import zero `javax.crypto.*` classes.
