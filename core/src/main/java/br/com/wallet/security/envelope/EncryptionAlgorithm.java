package br.com.wallet.security.envelope;

import java.util.Objects;

/**
 * Supported authenticated encryption algorithms for envelope encryption (REQ-SEC-020).
 */
public enum EncryptionAlgorithm {
    AES_256_GCM("AES_256_GCM");

    private final String code;

    EncryptionAlgorithm(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static EncryptionAlgorithm fromCode(String code) {
        Objects.requireNonNull(code, "code must not be null");
        for (EncryptionAlgorithm alg : values()) {
            if (alg.code.equals(code)) {
                return alg;
            }
        }
        throw new IllegalArgumentException("Unsupported encryption algorithm: " + code);
    }
}
