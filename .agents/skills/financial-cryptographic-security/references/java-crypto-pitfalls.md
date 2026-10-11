# ⚠️ Java Cryptography Pitfalls & Mitigations

## 1. Mutable Byte Arrays in Java Records
- **Pitfall**: Java `record` components that are arrays (`byte[]`) are shallow-immutable. Any caller calling `envelope.ciphertext()[0] = 0` mutates the record in-place.
- **Mitigation**: Wrap byte arrays in dedicated value objects (e.g. `CryptoBytes`) with defensive cloning on construction and access, or perform strict defensive copies.

## 2. Weak Key Material Retention in JVM Heap
- **Pitfall**: Leaving raw keys as immutable `byte[]` or `SecretKey` references leaves keys in JVM heap until garbage collected, readable via memory dumps.
- **Mitigation**: Encapsulate sensitive DEK bytes in `SensitiveKeyMaterial implements AutoCloseable`, invoking `Arrays.fill(material, (byte) 0)` upon release/eviction.

## 3. Ambiguous Delimiter AAD Encoding
- **Pitfall**: Formatting AAD via `String.format("%s|%s", tenant, opId)` breaks if `tenant` contains `|`.
- **Mitigation**: Use length-prefixed binary serialization (`short len` + `byte[] bytes`).

## 4. GCM Nonce Reuse Catastrophe
- **Pitfall**: Reusing the same 12-byte IV with the same AES key reveals XOR of plaintexts and allows recovery of the GHASH authentication key.
- **Mitigation**: Enforce CSPRNG 12-byte IV generation (`SecureRandom`) for every encryption; never permit caller-supplied IVs.

## 5. Non-Constant-Time Digest Comparisons
- **Pitfall**: Comparing signatures with `String.equals()` or `Arrays.equals()` leaks timing information.
- **Mitigation**: Always use `MessageDigest.isEqual()`.
