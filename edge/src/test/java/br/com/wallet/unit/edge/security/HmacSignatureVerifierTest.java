package br.com.wallet.unit.edge.security;

import br.com.wallet.edge.api.CredentialMaterial;
import br.com.wallet.edge.internal.security.HmacCanonicalizer;
import br.com.wallet.edge.internal.security.HmacSignatureVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HmacSignatureVerifier Tests (I-SEC-002, REQ-SEC-002, TASK-SEC-2.4)")
class HmacSignatureVerifierTest {

    private static final byte[] SECRET = "secret-key-for-testing-32-bytes!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_SECRET = "different-secret-key-32-bytes!!".getBytes(StandardCharsets.UTF_8);

    private final HmacSignatureVerifier verifier = new HmacSignatureVerifier();

    @Test
    @DisplayName("Should verify valid signature successfully")
    void shouldVerifyValidSignatureSuccessfully() {
        String canonicalRequest = HmacCanonicalizer.buildCanonicalRequest(
                "POST",
                "/operations/transfer",
                null,
                "key-1",
                "1726860000000",
                "op-100",
                "{\"amount\":50.00}".getBytes(StandardCharsets.UTF_8)
        );

        String signature = verifier.computeSignatureHex(canonicalRequest, new CredentialMaterial(SECRET));

        assertThat(verifier.verify(canonicalRequest, signature, new CredentialMaterial(SECRET))).isTrue();
    }

    @Test
    @DisplayName("Should reject tampered canonical request")
    void shouldRejectTamperedCanonicalRequest() {
        String canonicalRequest = HmacCanonicalizer.buildCanonicalRequest(
                "POST",
                "/operations/transfer",
                null,
                "key-1",
                "1726860000000",
                "op-100",
                "{\"amount\":50.00}".getBytes(StandardCharsets.UTF_8)
        );

        String signature = verifier.computeSignatureHex(canonicalRequest, new CredentialMaterial(SECRET));

        String tamperedRequest = HmacCanonicalizer.buildCanonicalRequest(
                "POST",
                "/operations/transfer",
                null,
                "key-1",
                "1726860000000",
                "op-100",
                "{\"amount\":5000.00}".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(verifier.verify(tamperedRequest, signature, new CredentialMaterial(SECRET))).isFalse();
    }

    @Test
    @DisplayName("Should reject signature created with different secret")
    void shouldRejectSignatureWithDifferentSecret() {
        String canonicalRequest = HmacCanonicalizer.buildCanonicalRequest(
                "POST",
                "/operations/transfer",
                null,
                "key-1",
                "1726860000000",
                "op-100",
                "{\"amount\":50.00}".getBytes(StandardCharsets.UTF_8)
        );

        String signature = verifier.computeSignatureHex(canonicalRequest, new CredentialMaterial(OTHER_SECRET));

        assertThat(verifier.verify(canonicalRequest, signature, new CredentialMaterial(SECRET))).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid signature length safely without throwing exception")
    void shouldRejectInvalidSignatureLengthSafely() {
        String canonicalRequest = "dummy-canonical-request";
        CredentialMaterial material = new CredentialMaterial(SECRET);

        assertThat(verifier.verify(canonicalRequest, "too-short", material)).isFalse();
        assertThat(verifier.verify(canonicalRequest, "invalid-hex-chars-not-64-length!", material)).isFalse();
        assertThat(verifier.verify(canonicalRequest, "a".repeat(63), material)).isFalse();
        assertThat(verifier.verify(canonicalRequest, "a".repeat(65), material)).isFalse();
        assertThat(verifier.verify(canonicalRequest, "z".repeat(64), material)).isFalse(); // non-hex character
    }

    @Test
    @DisplayName("Should reject null or blank signature safely")
    void shouldRejectNullOrBlankSignatureSafely() {
        String canonicalRequest = "dummy-canonical-request";
        CredentialMaterial material = new CredentialMaterial(SECRET);

        assertThat(verifier.verify(canonicalRequest, null, material)).isFalse();
        assertThat(verifier.verify(canonicalRequest, "", material)).isFalse();
        assertThat(verifier.verify(canonicalRequest, "   ", material)).isFalse();
        assertThat(verifier.verify(null, "some-sig", material)).isFalse();
        assertThat(verifier.verify(canonicalRequest, "some-sig", null)).isFalse();
    }
}
