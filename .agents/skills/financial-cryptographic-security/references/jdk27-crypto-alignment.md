# ☕ JDK 27 Security Alignment & Post-Quantum Cryptography (PQC) Reference

- **Release Date**: September 15, 2026
- **JDK Version**: OpenJDK / Oracle JDK 27
- **Key Enhancements**: JEP 527 (Post-Quantum Hybrid TLS 1.3), JEP 538 (PEM API Preview 3), KeyStore `Instant` API, Curve25519 / ML-KEM accelerations, `jcmd VM.security_properties`.

---

## 1. Post-Quantum Hybrid TLS 1.3 (JEP 527 / RFC 10024)

### 1.1 Overview & Threat Model
Standard TLS 1.3 key exchange relies on classical Diffie-Hellman (ECDH) curves (e.g. X25519, SecP256r1), which are vulnerable to future Cryptanalytically Relevant Quantum Computers (CRQCs) using Shor's algorithm. Under the **"Harvest Now, Decrypt Later" (HNDL)** attack model, adversaries record encrypted network traffic today with the intention of decrypting it when quantum hardware becomes available.

JDK 27 introduces native hybrid key exchange in `javax.net.ssl`, combining classical and quantum-resistant algorithms:
- **`X25519MLKEM768`**: Combines classical X25519 with ML-KEM-768 (NIST FIPS 203 / Kyber). **Enabled out of the box by default in JDK 27**.
- **`SecP256r1MLKEM768`**: Combines NIST P-256 with ML-KEM-768.
- **`SecP384r1MLKEM1024`**: Combines NIST P-384 with ML-KEM-1024.

### 1.2 Platform Architectural Impact
1. **Edge Ingress**: Client connections over TLS 1.3 or HTTP/3 over QUIC on UDP 8443 automatically negotiate `X25519MLKEM768` without code modifications. Financial command payloads in transit are quantum-shielded at the transport layer.
2. **NATS Messaging Fabric**: Inter-process communication between Edge and Core over TLS automatically utilizes hybrid PQC key exchange.
3. **No Code Downgrades**: TLS configurations must not restrict `jdk.tls.namedGroups` to legacy curves, preserving default hybrid negotiation.

---

## 2. Standardized PEM API (JEP 538 — Third Preview)

JDK 27 introduces `java.security.PEMEncoder` and `java.security.PEMDecoder`, standardizing PEM serialization for certificates, keys, and encrypted key material without third-party dependencies (e.g. BouncyCastle):

```java
// Encoding a certificate or public key to PEM
PEMEncoder encoder = PEMEncoder.of();
String pemCert = encoder.encodeToString(x509Cert);

// Decoding PEM back to strongly typed cryptographic object
PEMDecoder decoder = PEMDecoder.of();
X509Certificate cert = decoder.decode(pemCert, X509Certificate.class);

// Encrypting a private key with password and encoding in one step
PEMEncoder encEncoder = PEMEncoder.of().withEncryption(password);
String encryptedPemKey = encEncoder.encodeToString(privateKey);

// Decoding and decrypting in one step
PEMDecoder decDecoder = PEMDecoder.of().withDecryption(password);
PrivateKey decryptedKey = decDecoder.decode(encryptedPemKey, PrivateKey.class);
```

### Application in Wallet Service
- In **`LocalApplianceKeyManagementClient`**, local root KEKs and certificates can be securely stored and parsed using JDK 27 standard `PEMEncoder`/`PEMDecoder`, maintaining strict zero-external-crypto-dependencies (`I-SEC-015`).

---

## 3. KeyStore `Instant` Lifecycle API (JDK-8374808)

JDK 27 replaces legacy mutable `java.util.Date` with immutable `java.time.Instant`:
- `KeyStore.getCreationInstant(String alias)`
- `KeyStoreSpi.engineGetCreationInstant(String alias)`

### Application in Wallet Service
- In `KeyManagementClient` and `DekCacheEntry`, creation and expiration timestamps are natively typed as `java.time.Instant`, preventing mutable state leaks.

---

## 4. Operational Diagnostics: `jcmd VM.security_properties`

JDK 27 provides a diagnostic command to inspect all active JVM security properties:
```bash
# Verify active security properties on running Edge or Core container
jcmd <pid> VM.security_properties
```

Output verification highlights:
- `crypto.policy=unlimited`
- `jdk.tls.keyLimits=AES/GCM/NoPadding KeyUpdate 2^37, ChaCha20-Poly1305 KeyUpdate 2^37`
- `securerandom.strongAlgorithms=NativePRNGBlocking:SUN,DRBG:SUN`
- `jdk.quic.tls.keyLimits=AES/GCM/NoPadding 2^23`

---

## 5. Post-Quantum Invariance for Symmetric Envelope Encryption

### Grover's Algorithm & AES-256
Grover's algorithm reduces the brute-force search space of symmetric ciphers from $2^k$ to $2^{k/2}$:
- **AES-128**: Reduced to $2^{64}$ operations (potentially vulnerable to quantum computers).
- **AES-256**: Reduced to $2^{128}$ operations (remains completely intractable for any quantum computer).

`SPEC-000.10` enforces **AES-256-GCM** exclusively (`I-ENV-001`), ensuring that encrypted payloads at rest in `/spool` and in-transit over NATS are **quantum-safe by design**.
