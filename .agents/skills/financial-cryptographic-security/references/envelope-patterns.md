# 📐 Envelope Encryption Patterns & Formats

## 1. Two-Tier Envelope Model

In financial systems handling high-throughput ingress, calling a centralized KMS per request incurs unacceptable network latency ($5\text{--}50\text{ms}$) and cost. Envelope encryption separates the key hierarchy into:
1. **Key Encryption Key (KEK)**: Root master key residing securely inside Hardware Security Modules (HSMs) or cloud KMS (AWS KMS, GCP Cloud KMS, HashiCorp Vault). Never leaves KMS boundaries in plaintext.
2. **Data Encryption Key (DEK)**: 256-bit symmetric key (`AES/GCM/NoPadding`) generated on-demand or leased from a bounded in-memory cache. Used to encrypt command payloads.

## 2. Two-Tier Binding: KMS Context + Canonical AAD

### Tier 1: KMS Encryption Context
KMS Data Key generation and unwrapping are cryptographically bound to tenant identity:
$$\text{KmsEncryptionContext} = \{\text{"WALLET-ENV-V1"}, \text{"tenant\_id"}: \text{tenantId}, \text{"key\_domain"}: \text{"WALLET-ENV-V1"}\}$$
Presenting Tenant A's wrapped DEK under Tenant B's context causes KMS unwrap to fail.

### Tier 2: Length-Prefixed Canonical AAD
Constructed with exact pre-calculated buffer capacity and explicit 32-bit field lengths:
$$\text{AAD} = \text{Bytes}(\text{"WALLET-ENV-AAD-V1"} \,\|\, \text{Len}(v) \,\|\, v \,\|\, \text{Len}(t) \,\|\, t \,\|\, \text{Len}(op) \,\|\, op \,\|\, \text{Len}(k) \,\|\, k \,\|\, \text{Len}(alg) \,\|\, alg)$$

## 3. Structural Validation Rules

`CryptoEnvelope` enforces strict structural validation upon construction:
- `version` must be supported (`WALLET_ENV_V1`)
- `algorithm` must be supported (`AES_256_GCM`)
- `iv.length() == 12` (96-bit NIST SP 800-38D requirement)
- `wrappedDek.length() > 0`
- `ciphertext.length() >= 16` (minimum 128-bit authentication tag)
- All metadata fields (`tenantId`, `operationId`, `keyId`) non-null and valid.
