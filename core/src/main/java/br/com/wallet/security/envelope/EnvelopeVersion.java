package br.com.wallet.security.envelope;

import java.util.Objects;

/**
 * Protocol version identifier for cryptographic envelopes (REQ-SEC-020).
 */
public enum EnvelopeVersion {
    WALLET_ENV_V1("WALLET-ENV-V1");

    private final String code;

    EnvelopeVersion(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static EnvelopeVersion fromCode(String code) {
        Objects.requireNonNull(code, "code must not be null");
        for (EnvelopeVersion version : values()) {
            if (version.code.equals(code)) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unknown envelope version: " + code);
    }
}
