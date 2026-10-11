package br.com.wallet.unit.edge.security;

import br.com.wallet.edge.internal.security.HmacCanonicalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HmacCanonicalizer Tests (REQ-SEC-002, I-SEC-002)")
class HmacCanonicalizerTest {

    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    @DisplayName("Should canonicalize standard POST request with exact 8 lines and no trailing newline")
    void shouldCanonicalizeStandardPostRequest() {
        byte[] body = "{\"amount\":100.00}".getBytes(StandardCharsets.UTF_8);
        String bodySha256 = HmacCanonicalizer.sha256Hex(body);

        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                "POST",
                "/operations/transfer",
                null,
                "key-123",
                "1726860000000",
                "op-uuid-456",
                body
        );

        String expected = "WALLET-HMAC-V1\n" +
                "POST\n" +
                "/operations/transfer\n" +
                "\n" +
                "key-123\n" +
                "1726860000000\n" +
                "op-uuid-456\n" +
                bodySha256;

        assertThat(canonical).isEqualTo(expected);
        assertThat(canonical).doesNotEndWith("\n");
        assertThat(canonical.split("\n", -1)).hasSize(8);
    }

    @Test
    @DisplayName("Should sort query parameters lexicographically by name and value")
    void shouldSortQueryParamsLexicographically() {
        String canonical1 = HmacCanonicalizer.buildCanonicalRequest(
                "GET",
                "/operations/query",
                "b=2&a=1&z=last&a=0",
                "key-1",
                "1726860000000",
                null,
                new byte[0]
        );

        String canonical2 = HmacCanonicalizer.buildCanonicalRequest(
                "GET",
                "/operations/query",
                "a=0&z=last&b=2&a=1",
                "key-1",
                "1726860000000",
                null,
                new byte[0]
        );

        assertThat(canonical1).isEqualTo(canonical2);
        String[] lines = canonical1.split("\n", -1);
        assertThat(lines[3]).isEqualTo("a=0&a=1&b=2&z=last");
    }

    @Test
    @DisplayName("Should handle empty query parameters as empty line")
    void shouldHandleEmptyQueryParameters() {
        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                "GET",
                "/operations/query",
                "",
                "key-1",
                "1726860000000",
                null,
                null
        );

        String[] lines = canonical.split("\n", -1);
        assertThat(lines[3]).isEmpty();
        assertThat(lines[7]).isEqualTo(EMPTY_SHA256);
    }

    @Test
    @DisplayName("Should normalize URI path according to RFC 3986")
    void shouldNormalizeUriPath() {
        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                "post",
                "/foo/../operations/transfer/",
                null,
                "key-1",
                "1726860000000",
                "op-1",
                new byte[0]
        );

        String[] lines = canonical.split("\n", -1);
        assertThat(lines[1]).isEqualTo("POST");
        assertThat(lines[2]).isEqualTo("/operations/transfer");
    }

    @Test
    @DisplayName("Should use empty string for null operationId")
    void shouldUseEmptyStringForNullOperationId() {
        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                "GET",
                "/operations/123/stream",
                null,
                "key-1",
                "1726860000000",
                null,
                null
        );

        String[] lines = canonical.split("\n", -1);
        assertThat(lines[6]).isEmpty();
    }
}
