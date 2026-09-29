# 🔄 Cryptographic & Protocol Replay Protection

## 1. Disambiguation: IV vs Replay Nonce

1. **AES-GCM IV (`iv`)**:
   - Primitive layer.
   - 12-byte (96-bit) CSPRNG vector from `SecureRandom`.
   - Purpose: Ensure identical plaintexts encrypted under the same DEK produce completely distinct ciphertexts and tags.
   - Invariant: $(K_{\text{DEK}}, \text{IV})$ MUST NOT repeat (`I-ENV-006`).
2. **Protocol Nonce (`X-Nonce`)**:
   - Ingress transport layer.
   - Alphanumeric string/UUID provided by client in HTTP request header.
   - Purpose: Ensure identical signed requests cannot be replayed across network boundaries.

## 2. Two-Phase Nonce Reservation Pattern (`I-ENV-004`)

To prevent transient infrastructure failures (KMS timeout, journal append failure) from turning into permanent 401 replay rejections on client retry:

```text
1. HMAC Authentication (WALLET-HMAC-V1)
2. NonceTracker.reserve(ReplayKey) -> NonceReservation
   ├── If RESERVED or REJECTED -> Return HTTP 401 (DUPLICATE_NONCE)
   └── If ADMITTED:
          ├── Acquire DEK & Encrypt Payload
          ├── Write to SegmentedFileJournal & Fsync
          │      ├── SUCCESS -> NonceTracker.commit(ReplayKey) -> Return HTTP 202
          │      └── FAILURE -> NonceTracker.release(ReplayKey) -> Return HTTP 500/503
```

## 3. Required Verification

- `NonceAdmissionFailureRecoveryTest`:
  1. Nonce reserved $\to$ KMS failure $\to$ release nonce $\to$ client retry with same nonce succeeds.
  2. Nonce reserved $\to$ journal write failure $\to$ release nonce $\to$ retry succeeds.
  3. Nonce reserved $\to$ successful journal fsync $\to$ committed $\to$ duplicate retry rejected with 401.
