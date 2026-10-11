# 🔑 Key Management & Lifecycle Architecture

## 1. Key Hierarchy & Permissions

- **Edge Tier**:
  - Requires IAM permission: `kms:GenerateDataKey`.
  - Can request plaintext DEK + wrapped DEK from KMS with `KmsEncryptionContext`.
  - CANNOT unwrap wrapped DEKs (no `kms:Decrypt` permission).
- **Core Tier**:
  - Requires IAM permission: `kms:Decrypt`.
  - Can unwrap wrapped DEKs sent in `CryptoEnvelope` using `KmsEncryptionContext`.
  - CANNOT accept unauthenticated external ingress.
- **Ledger Tier**:
  - Has ZERO KMS permissions. Does not touch KMS or cryptographic envelopes.

## 2. Bounded Plaintext DEK Cache Semantics

To achieve sub-millisecond admission throughput while adhering to NIST GCM invocation budgets:
- **Cache Key**: `DekCacheKey(TenantId tenantId, KeyId keyId)`
- **Cache Value**: `DekCacheEntry(tenantId, keyId, wrappedDek, SensitiveKeyMaterial plaintextDek, createdAt, expiresAt, AtomicLong encryptionCount)`
- **Max Lifetime**: 10 minutes (`expireAfterWrite`).
- **Max Invocations**: 100,000 encryptions per DEK before mandatory eviction (`I-ENV-006`).
- **Cache Eviction**: Automatic zeroization via `SensitiveKeyMaterial.close()`.
- **IV Generation**: Each encryption MUST draw a fresh 12-byte CSPRNG IV from `SecureRandom`.

## 3. Technology Adapters in Infrastructure

All concrete KMS clients and caching layers reside strictly in `br.com.wallet.infrastructure.security.keymanagement`:
- `LocalApplianceKeyManagementClient`: Generates cryptographically secure random root KEK.
- `CachedKeyManagementClient`: Encapsulates the bounded plaintext DEK cache.
- `AwsKmsClient`, `VaultKmsClient`: Cloud KMS adapters.
- `InMemoryKeyManagementClient`: Resides exclusively in `src/test/java` for deterministic test suites.
