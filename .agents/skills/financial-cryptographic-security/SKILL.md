---
name: financial-cryptographic-security
description: Application-layer envelope encryption, AEAD with canonical AAD, secure key material lifecycle, replay protection algebra, zero-plaintext journals, telemetry non-emission, and Spring Modulith cryptographic isolation.
---

# 🔐 Financial Cryptographic Security Skill

## 1. Identity & Architectural Mantra

This skill guides the design, review, implementation, and verification of **application-layer envelope encryption**, **two-phase cryptographic replay reservation**, **secure key material lifecycle**, **zero-plaintext journals**, and **telemetry protection** across the Wallet platform without introducing cryptographic dependencies into the authoritative ledger.

> *"Financial Security protects financial data; it does not own financial state. Cryptographic authentication and payload confidentiality must complete before the financial database transaction begins. Security owns the security contracts; infrastructure owns technology adapters."*

---

## 2. Core Cryptographic Invariants & Guardrails

| Invariant ID | Name | Formal Invariant Rule / Enforcement |
| :--- | :--- | :--- |
| **`I-ENV-001`** | **Zero Plaintext at Rest & In-Transit** | $\text{Plaintext} \cap \text{SpoolDiskBytes} = \emptyset$. All commands in Edge journals (`/spool`) and NATS JetStream are strictly length-prefixed `CryptoEnvelope` byte sequences. |
| **`I-ENV-002`** | **Canonical AAD & KMS Context Binding** | Two-tier binding: KMS wrapped DEK bound to `KmsEncryptionContext(tenantId, keyDomain)`. GCM ciphertext bound to length-prefixed `CanonicalAad(version, tenantId, opId, keyId, alg)`. |
| **`I-ENV-003`** | **Decrypt-Before-Transaction** | $\text{BeginTx}() \succ \text{EnvelopeDecryptor.decrypt}(\text{CryptoEnvelope})$. Zero KMS, cache, or cryptographic network I/O while holding database connection or row-level locks on `accounts`. |
| **`I-ENV-004`** | **Two-Phase Replay Reservation** | Replay lifecycle: `reserve()` post-HMAC $\to$ `commit()` upon durable journal fsync $\to$ `release()` on pre-journal failure to prevent transient errors becoming permanent 401 rejections. |
| **`I-ENV-005`** | **Benchmark Latency Envelope** | Target $P99 \le 10\mu\text{s}$ for primitive AES-256-GCM cipher and $P99 \le 50\mu\text{s}$ for Edge admission crypto pipeline across 256B, 1KB, 2KB, 8KB, 64KB payloads. |
| **`I-ENV-006`** | **GCM IV Uniqueness & Invocation Budget** | For every encryption under a given key, $(K_{\text{DEK}}, \text{IV})$ MUST NOT repeat. 96-bit CSPRNG IVs via `SecureRandom`; DEK usage capped at 100,000 encryptions. |
| **`I-SEC-011`** | **KMS Air-Gap & Key Isolation** | Master KEKs never leave KMS. Edge holds `generateDataKey` permission; Core holds `decryptDataKey` permission; Ledger holds neither. |
| **`I-SEC-012`** | **Non-Emission First Telemetry** | Sensitive financial data (`amount`, account IDs, PII, keys) MUST NOT enter telemetry at the source (`toString()`, exceptions, MDC, spans). Filters serve as defense-in-depth. |
| **`I-SEC-013`** | **Financial Security Encapsulation** | All cryptographic contracts and pure engines reside in `br.com.wallet.security`, verified via Spring Modulith `ApplicationModules.verify()`. |
| **`I-SEC-014`** | **Ledger Architectural Isolation** | $\text{Deps}(\text{br.com.wallet.ledger}) \cap \text{Deps}(\text{br.com.wallet.security}) = \emptyset$. Ledger domain has zero imports or awareness of crypto envelopes. |
| **`I-SEC-015`** | **Dual Boundary & Provider Isolation** | Layer 1 Gradle build boundaries + Layer 2 Spring Modulith verification. Technology adapters (`DragonflyNonceTracker`, Cloud KMS) reside in `infrastructure`. |
| **`I-SEC-016`** | **Cryptographic Value Immutability & Key Zeroization** | `CryptoBytes` enforces defensive copying on constructor and accessor. `SensitiveKeyMaterial` auto-closes with memory zeroization (`Arrays.fill(material, (byte) 0)`). |

---

## 3. Cryptographic Domain Separation

| Domain Identifier | Target Purpose | Primitive & Key Material | Governing Scope |
| :--- | :--- | :--- | :--- |
| **`WALLET-HMAC-V1`** | Perimeter request authentication & integrity | HMAC-SHA-256 with long-lived Tenant API Secret (`javax.crypto.Mac`) | Client $\to$ Edge Perimeter |
| **`WALLET-NONCE-V1`** | Replay protection key namespace | `nonce:{tenantId}:{principalId}:{nonce}` in DragonflyDB (TTL 60s) | Edge $\to$ DragonflyDB |
| **`WALLET-ENV-V1`** | Application-layer payload confidentiality & authenticity | AES-256-GCM with Ephemeral DEK wrapped by Tenant KEK in KMS | Edge Spool $\to$ NATS $\to$ Core |
| **`WALLET-LEDGER-HASH-V1`**| Immutable accounting tamper detection & audit chain | SHA-256 sequential hash-chaining ($\text{hash}_n = \text{SHA256}(\dots)$) | Core $\to$ PostgreSQL `ledger` |

---

## 4. Immutable Cryptographic Value Objects & Memory Hygiene

### 4.1 Immutable Binary Value (`CryptoBytes`)
```java
public record CryptoBytes(byte[] value) {
    public CryptoBytes {
        Objects.requireNonNull(value, "value must not be null");
        value = value.clone(); // Defensive copy on construction
    }

    @Override
    public byte[] value() {
        return value.clone(); // Defensive copy on access
    }

    public int length() {
        return value.length;
    }
}
```

### 4.2 Strongly Validated `CryptoEnvelope`
```java
public record CryptoEnvelope(
    EnvelopeVersion version,           // WALLET_ENV_V1
    TenantId tenantId,                 // Derived from authenticated principal
    OperationId operationId,           // Client Idempotency-Key UUID
    KeyId keyId,                       // KMS KEK identifier
    EncryptionAlgorithm algorithm,     // AES_256_GCM
    CryptoBytes iv,                    // Exactly 12 bytes CSPRNG
    CryptoBytes wrappedDek,            // Wrapped DEK ciphertext
    CryptoBytes ciphertext             // Payload ciphertext + 16 bytes GCM auth tag
) {
    public CryptoEnvelope {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(iv, "iv must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        if (iv.length() != 12) {
            throw new IllegalArgumentException("GCM IV must be exactly 12 bytes (96 bits)");
        }
        if (wrappedDek.length() == 0) {
            throw new IllegalArgumentException("wrappedDek must not be empty");
        }
        if (ciphertext.length() < 16) {
            throw new IllegalArgumentException("ciphertext must be at least 16 bytes (auth tag)");
        }
    }
}
```

### 4.3 Sensitive Key Material Lifecycle (`SensitiveKeyMaterial`)
```java
public final class SensitiveKeyMaterial implements AutoCloseable {
    private final byte[] material;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public SensitiveKeyMaterial(byte[] rawKey) {
        this.material = rawKey.clone();
    }

    public byte[] getEncoded() {
        if (destroyed.get()) throw new IllegalStateException("Key material has been destroyed");
        return material.clone();
    }

    @Override
    public void close() {
        if (destroyed.compareAndSet(false, true)) {
            Arrays.fill(material, (byte) 0); // Best-effort JVM zeroization
        }
    }
}
```

---

## 5. Two-Tier Cryptographic Binding: KMS Context + Canonical AAD

### 5.1 Tier 1: KMS Encryption Context
KMS Data Key generation and unwrapping are cryptographically bound to tenant identity:
$$\text{KmsEncryptionContext} = \{\text{"WALLET-ENV-V1"}, \text{"tenant\_id"}: \text{tenantId}, \text{"key\_domain"}: \text{"WALLET-ENV-V1"}\}$$
Presenting Tenant A's wrapped DEK under Tenant B's context causes KMS unwrap to fail.

### 5.2 Tier 2: Length-Prefixed Canonical AAD
Constructed with exact pre-calculated buffer capacity and explicit 32-bit field lengths:
$$\text{AAD} = \text{Bytes}(\text{"WALLET-ENV-AAD-V1"} \,\|\, \text{Len}(v) \,\|\, v \,\|\, \text{Len}(t) \,\|\, t \,\|\, \text{Len}(op) \,\|\, op \,\|\, \text{Len}(k) \,\|\, k \,\|\, \text{Len}(alg) \,\|\, alg)$$

---

## 6. Two-Phase Replay Reservation Pattern (`I-ENV-004`)

To prevent transient infrastructure failures (KMS timeout, journal append failure) from turning into permanent 401 replay rejections on retry:

```text
HTTP Request Arrives
       │
       ├── 1. Check Timestamp Freshness (|now - X-Timestamp| <= 30,000ms)
       ├── 2. Verify HMAC-SHA256 Signature (WALLET-HMAC-V1)
       │      └── Failure -> Return HTTP 401 UNAUTHORIZED (Zero Dragonfly I/O)
       │
       └── 3. NonceTracker.reserve(ReplayKey) -> NonceReservation
              ├── If RESERVED or REJECTED -> Return HTTP 401 (DUPLICATE_NONCE)
              └── If ADMITTED:
                     ├── Acquire DEK & Encrypt Payload
                     ├── Write to SegmentedFileJournal & Fsync
                     │      ├── SUCCESS -> NonceTracker.commit(ReplayKey) -> Return HTTP 202
                     │      └── FAILURE -> NonceTracker.release(ReplayKey) -> Return HTTP 500/503
```

---

## 7. Bounded Plaintext DEK Cache Semantics (`REQ-SEC-026`)

- **Cache Key**: `DekCacheKey(TenantId tenantId, KeyId keyId)`
- **Cache Value**: `DekCacheEntry(tenantId, keyId, wrappedDek, SensitiveKeyMaterial plaintextDek, createdAt, expiresAt, AtomicLong encryptionCount)`
- **Max Lifetime**: 10 minutes (`expireAfterWrite`).
- **Max Invocations**: 100,000 encryptions per DEK before mandatory eviction (`I-ENV-006`).
- **Eviction Zeroization**: Evicted entries invoke `SensitiveKeyMaterial.close()`.
- **Mandatory IV Uniqueness**: Every encryption under a cached DEK MUST generate a fresh 12-byte CSPRNG IV via `SecureRandom` (`I-ENV-006`).

---

## 8. Modulith & Dual Boundary Enforcement

### Layer 1: Gradle Build Boundaries
- `:ledger` MUST NOT depend on `:security` (`I-SEC-014`).
- `:security` MUST NOT depend on `:edge`, `:core`, `:ledger`, or `:fraud`.
- `:edge` and `:core` depend strictly on `:security` API contracts (`I-SEC-015`).
- `:infrastructure` implements technology ports (`DragonflyNonceTracker`, Cloud KMS adapters, `LocalApplianceKeyManagementClient`, `CachedKeyManagementClient`).
- `InMemoryKeyManagementClient` is strictly test-scoped (`src/test/java`).

### Layer 2: Spring Modulith Architecture
- `br.com.wallet.security` is annotated with `@ApplicationModule(displayName = "Financial Security")`.
- Verified via `ApplicationModules.of(WalletApplication.class).verify()`.

---

## 9. 12-Step Crypto Boundary Review Protocol

```text
 1. Trust Boundaries: Verify TLS + HMAC at perimeter, authenticated NATS IPC, internal Core isolation.
 2. Key Boundaries: Verify KEK never leaves KMS; DEK wrapped in transport; Edge cannot decrypt.
 3. Plaintext Lifetime: Verify SensitiveKeyMaterial is auto-closed; plaintext DEK cache is bounded.
 4. Nonce/IV Lifecycle: Verify GCM IV is 12-byte CSPRNG; protocol nonce evaluated strictly post-HMAC with two-phase reservation.
 5. AAD Coverage: Verify tenantId, operationId, and keyId bound via length-prefixed canonical AAD.
 6. Transaction Boundary: Verify decryption and KMS calls complete strictly before BEGIN DB TX.
 7. Telemetry Boundary: Verify non-emission first policy; dedicated CryptoEnvelopeSummary; zero sensitive data in logs.
 8. Provider Isolation: Verify zero javax.crypto or cloud KMS SDK imports in domain or use cases.
 9. Value Immutability: Verify CryptoBytes defensive copies on construction and access; envelope validation.
10. Failure Taxonomy: Verify GCM tamper -> DLQ; KMS timeout -> retryable backoff; Replay -> 401.
11. Architecture Tests: Verify ApplicationModules.verify() and ArchUnit ledger isolation pass.
12. Benchmark Methodology: Verify latency targets measured across multiple payload sizes (256B to 64KB).
```
