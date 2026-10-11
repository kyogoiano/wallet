# 📝 Task Breakdown: TASKS-000.10 — Financial Security & Payload Cryptographic Protection

- **Associated Spec**: [`../SPEC-000.10-financial-security.md`](file:///.spec/SPEC-000.10-financial-security.md)
- **Associated Architecture**: [`../architecture/ARCH-000.10-financial-security.md`](file:///.spec/architecture/ARCH-000.10-financial-security.md)
- **Associated Plan**: [`../plans/PLAN-000.10-financial-security.md`](file:///.spec/plans/PLAN-000.10-financial-security.md)
- **Governing Skills**: [`financial-cryptographic-security`](file:///.agents/skills/financial-cryptographic-security/SKILL.md), [`perimeter-security`](file:///.agents/skills/perimeter-security/SKILL.md), [`spec-driven-development`](file:///.agents/skills/spec-driven-development/SKILL.md)
- **Status**: ✅ **Implemented & Verified (Rev. 2.2 — Aligned with Histories 71-74 & JDK 27 PQC Review)**
- **Execution Rule**: Execute all `[MUST]` tasks first. `[SHOULD]` and `[COULD]` are locked until `[MUST]` criteria are green (`I-SDD-004`).

---

## 1. Traceability Matrix

| Requirement / Invariant | Priority | Planned Verification Test | Task IDs |
| :--- | :--- | :--- | :--- |
| `REQ-SEC-020`, `I-SEC-016` | `[MUST]` | `CryptoEnvelopeImmutabilityTest`, `CryptoEnvelopeValidationTest` | `TASK-10.1` |
| `REQ-SEC-023`, `I-ENV-002` | `[MUST]` | `CanonicalAadGoldenVectorTest`, `CrossTenantEnvelopeSubstitutionTest` | `TASK-10.2` |
| `REQ-SEC-024`, `I-SEC-016` | `[MUST]` | `SensitiveKeyMaterialTest`, `KeyManagementClientTest` | `TASK-10.3`, `TASK-10.4` |
| `REQ-SEC-028`, `REQ-SEC-029`, `I-ENV-004` | `[MUST]` | `NonceAdmissionFailureRecoveryTest`, `DragonflyNonceTrackerTest` | `TASK-10.5`, `TASK-10.10`, `TASK-10.11` |
| `REQ-SEC-020`, `I-ENV-001`, `I-ENV-006` | `[MUST]` | `AesGcmEnvelopeEncryptorTest`, `GcmIvUniquenessTest` | `TASK-10.6` |
| `REQ-SEC-030`, `REQ-SEC-031`, `I-SEC-012` | `[MUST]` | `TelemetrySanitizationTest`, `CryptoEnvelopeSummaryTest` | `TASK-10.7` |
| `REQ-SEC-026`, `I-ENV-005`, `I-ENV-006` | `[MUST]` | `CachedKeyManagementClientTest` | `TASK-10.8` |
| `REQ-SEC-025`, JEP 538 | `[MUST]` | `LocalApplianceKmsPemTest`, `InMemoryKeyManagementClientTest` | `TASK-10.9` |
| `REQ-SEC-021`, `TASK-6.6`, `I-ENV-001` | `[MUST]` | `ZeroPlaintextSpoolJournalTest`, `BinaryRecordCodecEnvelopeTest` | `TASK-10.12` |
| `REQ-SEC-021`, JEP 527 | `[MUST]` | `NatsEdgeCommandPublisherSecurityTest` | `TASK-10.13` |
| `REQ-SEC-022`, `I-ENV-003` | `[MUST]` | `CoreDecryptBeforeTransactionTest` | `TASK-10.14` |
| Security Invariants Suite | `[MUST]` | `CrossTenantEnvelopeSubstitutionTest`, `CryptoEnvelopeSubstitutionTest` | `TASK-10.15` |
| `REQ-SEC-035` - `REQ-SEC-038`, `I-SEC-013` - `I-SEC-015` | `[MUST]` | `SecurityModulithArchitectureTest`, `LedgerIsolationArchitectureTest` | `TASK-10.16` |
| `I-ENV-005`, JDK 27 JEP 527 | `[MUST]` | `CryptoPipelineBenchmarkProfile`, `jcmd VM.security_properties` | `TASK-10.17` |

---

## 2. Active Task Card Protocol (Context Hygiene)

> [!TIP]
> When executing a task, focus strictly on the active task card below. Do not load unrelated modules into memory. Verify Red $\to$ Green $\to$ Refactor with deterministic assertions (`I-TDD-002`).

---

## 3. Implementation Tasks (TDD Order)

### Phase 1: Security Contracts & Cryptographic Foundation (`br.com.wallet.security`)

- [x] `TASK-10.1` [MUST]: Implement Immutable Cryptographic Values & Envelope Validation:
  - Create `br.com.wallet.security.envelope.CryptoBytes`: record wrapping `byte[]` with defensive cloning on construction and `.value()` access (`I-SEC-016`).
  - Create typed value objects: `EnvelopeVersion` (`WALLET_ENV_V1`), `TenantId`, `OperationId`, `KeyId`, `EncryptionAlgorithm` (`AES_256_GCM`).
  - Create `br.com.wallet.security.envelope.CryptoEnvelope` record with strict constructor validation:
    - `iv.length() == 12` (96-bit NIST SP 800-38D requirement).
    - `wrappedDek.length() > 0`.
    - `ciphertext.length() >= 16` (minimum 128-bit authentication tag).
    - Non-null validation for all fields.
  - Implement `CryptoEnvelopeImmutabilityTest`: assert mutating caller arrays before/after creation leaves envelope unaltered.
  - Implement `CryptoEnvelopeValidationTest`: assert invalid lengths throw `IllegalArgumentException`.

- [x] `TASK-10.2` [MUST]: Implement Length-Prefixed Canonical AAD Engine (`I-ENV-002`):
  - Create `br.com.wallet.security.envelope.CanonicalAad`:
    - Constructs byte sequence `WALLET-ENV-AAD-V1` + 4-byte big-endian field lengths + exact UTF-8 bytes for `version`, `tenantId`, `operationId`, `keyId`, `algorithm`.
    - Pre-calculates exact `ByteBuffer` capacity to eliminate reallocation overhead.
  - Implement `CanonicalAadGoldenVectorTest`:
    - Validate computed AAD against bitwise golden hex vectors.
    - Assert that identifier strings containing delimiter characters (`|`, `:`, `,`) serialize unambiguously.

- [x] `TASK-10.3` [MUST]: Implement Sensitive Key Material & AutoCloseable Key Pair (`I-SEC-016`):
  - Create `br.com.wallet.security.keymanagement.SensitiveKeyMaterial`:
    - Holds `byte[] material` with defensive clone.
    - Implements `AutoCloseable`: `close()` invokes `Arrays.fill(material, (byte) 0)` via `AtomicBoolean destroyed`.
    - Throws `IllegalStateException` on `.getEncoded()` if accessed after `close()`.
  - Create `br.com.wallet.security.keymanagement.GeneratedDataKey`:
    - Record holding `SensitiveKeyMaterial plaintextDek` and `CryptoBytes wrappedDek`.
    - Implements `AutoCloseable`: delegates `close()` to `plaintextDek`.
  - Implement `SensitiveKeyMaterialTest`: assert zeroization and post-close access exceptions.

- [x] `TASK-10.4` [MUST]: Implement Typed `KeyManagementClient` SPI & KMS Context (`I-SEC-011`):
  - Create `br.com.wallet.security.keymanagement.KeyContext`:
    - Factory `KeyContext.forTenant(TenantId tenantId)` producing KMS Encryption Context `{"WALLET-ENV-V1", "tenant_id": tenantId, "key_domain": "WALLET-ENV-V1"}`.
  - Create interface `KeyManagementClient`:
    - `GeneratedDataKey generateDataKey(TenantId tenantId, KeyId keyId, KeyContext context);`
    - `SensitiveKeyMaterial decryptDataKey(TenantId tenantId, KeyId keyId, CryptoBytes wrappedDek, KeyContext context);`

- [x] `TASK-10.5` [MUST]: Implement Two-Phase Nonce Replay Protocol SPI (`I-ENV-004`):
  - Create `br.com.wallet.security.replay.ReplayKey`: value object `(TenantId tenantId, PrincipalId principalId, String nonce)`.
  - Create sealed interface `br.com.wallet.security.replay.NonceReservation`:
    - `record Admitted() implements NonceReservation {}`
    - `record Rejected(ReplayRejectionReason reason) implements NonceReservation {}`
    - `record Unavailable(ReplayAvailabilityReason reason) implements NonceReservation {}`
  - Create interface `br.com.wallet.security.replay.NonceTracker`:
    - `NonceReservation reserve(ReplayKey key);` (reserves nonce with 10s lease)
    - `void commit(ReplayKey key);` (extends TTL to 60s upon durable persistence)
    - `void release(ReplayKey key);` (releases reservation on pre-journal failure)

- [x] `TASK-10.6` [MUST]: Implement Pure AES-256-GCM Envelope Encryption & Decryption Engines:
  - Create `br.com.wallet.security.envelope.EnvelopeEncryptor`:
    - Uses `SecureRandom` to generate unique 12-byte CSPRNG IV (`I-ENV-006`).
    - Compiles `CanonicalAad.compute(envelope)`.
    - Executes `Cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv))`.
    - Updates AAD via `cipher.updateAAD(aad)` and outputs `CryptoEnvelope`.
  - Create `br.com.wallet.security.envelope.EnvelopeDecryptor`:
    - Reassembles canonical AAD from envelope.
    - Executes `Cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv))`.
    - Updates AAD and executes `cipher.doFinal()`. Throws `CryptographicIntegrityException` on tag mismatch.
  - Implement `AesGcmEnvelopeEncryptorTest`: positive encryption/decryption, tampered ciphertext, and tampered AAD.

- [x] `TASK-10.7` [MUST]: Implement Fine-Grained Failure Taxonomy & Safe Diagnostics (`I-SEC-012`):
  - Create fine-grained exception hierarchy in `br.com.wallet.security.failure`:
    - `CryptographicIntegrityException` (GCM tag or AAD tamper)
    - `KeyManagementUnavailableException` (retryable KMS timeout)
    - `ReplayDetectedException` (401 duplicate nonce)
    - `MalformedEnvelopeException` (400 invalid envelope bytes)
  - Create `br.com.wallet.security.telemetry.CryptoEnvelopeSummary`:
    - Safe diagnostic record: `(version, tenantId, operationId, keyId, ciphertextLength)`.
  - Implement `TelemetrySanitizationTest`: assert zero unmasked plaintexts or key bytes in exceptions, summaries, and logs.

---

### Phase 2: Technology Adapters (`br.com.wallet.infrastructure.security`)

- [x] `TASK-10.8` [MUST]: Implement `CachedKeyManagementClient` with Bounded Plaintext DEK Cache (`REQ-SEC-026`):
  - Create `DekCacheKey(TenantId tenantId, KeyId keyId)`.
  - Create `DekCacheEntry(tenantId, keyId, wrappedDek, SensitiveKeyMaterial plaintextDek, Instant createdAt, Instant expiresAt, AtomicLong encryptionCount)`.
  - Configure Caffeine cache with `expireAfterWrite(Duration.ofMinutes(10))` and removal listener invoking `plaintextDek.close()`.
  - Enforce maximum 100,000 encryptions per entry before forced eviction (`I-ENV-006`).
  - Implement `CachedKeyManagementClientTest`: verify cache hits, eviction zeroization, and 100k invocation rotation.

- [x] `TASK-10.9` [MUST]: Implement `LocalApplianceKeyManagementClient` & Test Support KMS (`REQ-SEC-025`):
  - Create `LocalApplianceKeyManagementClient` in `br.com.wallet.infrastructure.security.keymanagement`:
    - Generates a cryptographically secure random 256-bit root KEK stored locally.
    - Encrypts and decodes private keys using JDK 27 standard `PEMEncoder`/`PEMDecoder` (JEP 538).
    - Uses `KeyStore.getCreationInstant()` (JDK-8374808) for key creation timestamps.
  - Create `InMemoryKeyManagementClient` exclusively in `src/test/java/.../testsupport`:
    - Deterministic mock KEKs for reproducible unit testing.
  - Implement `LocalApplianceKmsPemTest`: test PEM encoding/decryption with zero third-party dependencies.

- [x] `TASK-10.10` [MUST]: Implement Two-Phase `DragonflyNonceTracker` Adapter (`REQ-SEC-029`):
  - Implement `reserve(ReplayKey key)`:
    - Redis command: `SET nonce:{tenant}:{principal}:{nonce} "RESERVED" NX EX 10`.
    - Returns `Admitted` on OK; `Rejected` if key exists; `Unavailable` on connection failure.
  - Implement `commit(ReplayKey key)`:
    - Redis command: `SET nonce:{tenant}:{principal}:{nonce} "COMMITTED" XX EX 60`.
  - Implement `release(ReplayKey key)`:
    - Redis command: `DEL nonce:{tenant}:{principal}:{nonce}`.
  - Implement `DragonflyNonceTrackerTest`: verify state transitions and atomic Redis semantics.

---

### Phase 3: Edge Ingress & Journal Persistence Integration (`:edge`)

- [x] `TASK-10.11` [MUST]: Integrate Two-Phase Nonce Reservation into `HmacAuthenticationFilter`:
  - After HMAC signature verification succeeds, invoke `nonceTracker.reserve(replayKey)`.
  - If `Rejected`: return `HTTP 401 UNAUTHORIZED` (`DUPLICATE_NONCE`).
  - If `Unavailable`: return `HTTP 503 SERVICE_UNAVAILABLE` (`REPLAY_STORAGE_UNAVAILABLE`).
  - Attach `ReplayKey` to request context for downstream commit/release.

- [x] `TASK-10.12` [MUST]: Update `SegmentedFileJournal` & `BinaryRecordCodec` for `CryptoEnvelope` (`TASK-6.6`, `I-ENV-001`):
  - In `br.com.wallet.edge.journal.BinaryRecordCodec`:
    - Persist command payload strictly as serialized length-prefixed `CryptoEnvelope` bytes inside the 54-byte record header.
    - On fsync completion, invoke `nonceTracker.commit(replayKey)`.
    - On IO / disk failure, invoke `nonceTracker.release(replayKey)`.
  - Implement `ZeroPlaintextSpoolJournalTest`:
    - Write command to journal $\to$ read raw segment bytes $\to$ assert zero occurrences of account IDs, balances, or names.

- [x] `TASK-10.13` [MUST]: Update `NatsEdgeCommandPublisher` to Publish Opaque `CryptoEnvelope` over PQC TLS:
  - Serialize `CryptoEnvelope` to NATS message payload.
  - Verify that NATS JetStream TLS connection supports JEP 527 Post-Quantum Hybrid TLS (`X25519MLKEM768`).
  - Implement `NatsEdgeCommandPublisherSecurityTest`.

---

### Phase 4: Core Consumer & Decrypt-Before-Transaction Integration (`:core` / Root)

- [x] `TASK-10.14` [MUST]: Implement Decrypt-Before-Transaction Pre-Gate in `CoreCommandConsumer` (`REQ-SEC-022`, `I-ENV-003`):
  - Extract `CryptoEnvelope` from NATS message.
  - Invoke `KeyManagementClient.decryptDataKey(wrappedDek, KmsEncryptionContext)` strictly outside any DB transaction.
  - Invoke `EnvelopeDecryptor.decrypt(envelope, dek)`.
  - On `CryptographicIntegrityException`: quarantine to DLQ under `CRYPTOGRAPHIC_TAMPER_DETECTED` and ACK NATS.
  - On `KeyManagementUnavailableException`: NACK with retry backoff.
  - Only upon successful decryption and command deserialization, begin `@Transactional` use case.
  - Implement `CoreDecryptBeforeTransactionTest`: assert zero DB connection acquisition during mock 500ms KMS latency.

---

### Phase 5: Verification Triads, Security Substitution Tests & ArchUnit Isolation

- [x] `TASK-10.15` [MUST]: Implement Comprehensive Security Substitution & Recovery Suite:
  - `NonceAdmissionFailureRecoveryTest`:
    - Test 1: Nonce reserved $\to$ simulated KMS failure $\to$ release nonce $\to$ client retry with same nonce succeeds.
    - Test 2: Nonce reserved $\to$ simulated journal write failure $\to$ release nonce $\to$ client retry succeeds.
    - Test 3: Nonce reserved $\to$ successful journal fsync $\to$ committed $\to$ duplicate retry rejected with 401.
  - `CrossTenantEnvelopeSubstitutionTest`:
    - Present Tenant A wrapped DEK under Tenant B KMS context $\to$ assert KMS unwrap failure.
    - Swap `tenantId` in AAD of authentic envelope $\to$ assert GCM authentication tag verification failure.
  - `CryptoEnvelopeSubstitutionTest`:
    - Swap ciphertext from Envelope B into Envelope A $\to$ assert tag failure.
    - Swap wrapped DEK from Envelope B into Envelope A $\to$ assert decryption failure.
  - `GcmIvUniquenessTest` (`I-ENV-006`):
    - Generate 1,000,000 IVs under same key $\to$ assert zero collisions.
    - Simulate parallel multi-instance Edge generation $\to$ assert non-overlapping CSPRNG streams.

- [x] `TASK-10.16` [MUST]: Implement Dual Boundary & Ledger Isolation Architecture Tests:
  - Verify Spring Modulith: `ApplicationModules.of(WalletApplication.class).verify()`.
  - ArchUnit Test 1: Assert `br.com.wallet.ledger..` has zero imports of `br.com.wallet.security..` (`I-SEC-014`).
  - ArchUnit Test 2: Assert `:edge` and `:core` import zero `javax.crypto.*` classes (`I-SEC-015`).
  - ArchUnit Test 3: Assert all concrete technology adapters reside in `br.com.wallet.infrastructure..`.

---

### Phase 6: Operational Convergence & Documentation

- [x] `TASK-10.17` [MUST]: Benchmark Profile & Runtime Security Property Inspection:
  - Execute micro-benchmark profiling for AES-256-GCM cipher across 256B, 1KB, 2KB, 8KB, 64KB payloads (`I-ENV-005`).
  - Verify containerized runtime security properties via `jcmd <pid> VM.security_properties` (`crypto.policy=unlimited`, `jdk.tls.keyLimits`).
- [x] `TASK-10.18` [MUST]: Author `SUMMARY-000.10.md` with Practical Verification Guide:
  - Bi-directional equivalence reconciliation (`I-SDD-003`).
  - Practical Verification Guide with curl signing examples, test fixtures, and expected responses (`I-SDD-002`).

---

## 4. Convergence & Verification Checklist (`I-SDD-002`, `I-SDD-003`)

### 4.1 Security Invariant Gates
- [x] Commands written to `/spool` contain zero plaintext financial data (`I-ENV-001`)
- [x] Two-tier cryptographic binding: KMS context binds tenant; canonical AAD binds payload metadata (`I-ENV-002`)
- [x] Core decrypts payload strictly before opening database transaction (`I-ENV-003`)
- [x] Two-phase nonce reservation prevents transient errors becoming permanent 401 rejections (`I-ENV-004`)
- [x] AES-GCM primitive target $P99 \le 10\mu\text{s}$, Edge pipeline target $P99 \le 50\mu\text{s}$ (`I-ENV-005`)
- [x] GCM IV is unique 96-bit CSPRNG; DEK usage capped at 100,000 encryptions (`I-ENV-006`)
- [x] Master KEKs never leave KMS; Edge holds generate, Core holds decrypt, Ledger holds neither (`I-SEC-011`)
- [x] Sensitive financial data does not enter logs, exceptions, or MDC (`I-SEC-012`)
- [x] Pure crypto contracts encapsulated in `br.com.wallet.security` (`I-SEC-013`)
- [x] Root `:ledger` has zero dependencies on `:security` (`I-SEC-014`)
- [x] Layer 1 Gradle build boundaries and Layer 2 Spring Modulith verification pass (`I-SEC-015`)
- [x] `CryptoBytes` enforces defensive copies; `SensitiveKeyMaterial` auto-closes with memory zeroization (`I-SEC-016`)
- [x] Post-Quantum Hybrid TLS 1.3 (`X25519MLKEM768`) verified at Edge ingress and NATS IPC
