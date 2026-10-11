package br.com.wallet.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersTest {

    @Test
    @DisplayName("Should define canonical security header constants")
    void shouldDefineCanonicalSecurityHeaders() {
        assertThat(SecurityHeaders.X_KEY_ID).isEqualTo("X-Key-Id");
        assertThat(SecurityHeaders.X_TIMESTAMP).isEqualTo("X-Timestamp");
        assertThat(SecurityHeaders.X_SIGNATURE).isEqualTo("X-Signature");
        assertThat(SecurityHeaders.IDEMPOTENCY_KEY).isEqualTo("Idempotency-Key");
        assertThat(SecurityHeaders.PROTOCOL_VERSION).isEqualTo("WALLET-HMAC-V1");
    }
}
