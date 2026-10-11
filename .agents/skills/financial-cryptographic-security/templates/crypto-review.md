# 📋 Cryptographic Boundary Review Checklist

- **Target Component / Class**: 
- **Cryptographic Purpose**: `WALLET-ENV-V1` | `WALLET-HMAC-V1` | `WALLET-NONCE-V1` | `WALLET-LEDGER-HASH-V1`
- **Reviewer**: 
- **Date**: 

---

## 1. 12-Step Cryptographic Verification

| Step | Check Item | Result | Notes / Evidence |
| :--- | :--- | :--- | :--- |
| **1** | **Trust Boundaries** | PASS / FAIL | TLS + HMAC at ingress; authenticated NATS; Core isolation. |
| **2** | **Key Boundaries** | PASS / FAIL | KEK never exported; DEK wrapped; Edge cannot decrypt. |
| **3** | **Plaintext Lifetime** | PASS / FAIL | Plaintext DEK in `SensitiveKeyMaterial`; auto-closed; cache bounded. |
| **4** | **Nonce / IV Lifecycle** | PASS / FAIL | 12-byte CSPRNG IV; protocol nonce checked strictly post-HMAC. |
| **5** | **AAD Coverage** | PASS / FAIL | Canonical length-prefixed AAD binds tenant, opId, keyId. |
| **6** | **Transaction Boundary**| PASS / FAIL | Decryption completes strictly before `BEGIN TRANSACTION`. |
| **7** | **Telemetry Boundary** | PASS / FAIL | Non-emission first; zero amounts/keys in logs/spans/exceptions. |
| **8** | **Provider Isolation** | PASS / FAIL | Clean SPI; adapters in infrastructure; zero crypto SDK in ledger. |
| **9** | **Value Immutability** | PASS / FAIL | Defensive copies in `CryptoBytes`; no mutable byte arrays in records. |
| **10**| **Failure Taxonomy** | PASS / FAIL | Cryptographic failures quarantined to DLQ; 401 on perimeter auth. |
| **11**| **Architecture Tests** | PASS / FAIL | Modulith and ArchUnit rules pass without warnings. |
| **12**| **Benchmark Targets** | PASS / FAIL | Performance verified across payload sizes (256B to 64KB). |
