package br.com.wallet.copilot;

import br.com.wallet.copilot.internal.util.ParametersHashUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Parameters Hash Canonicalization & Determinism Tests (TASK-4.3, I-AI-007)")
class ParametersHashUtilTest {

    @Test
    @DisplayName("Should produce valid 64-character lowercase hex SHA-256")
    void shouldProduceValidSha256Hex() {
        String json = "{\"amount\": 100.00, \"currency\": \"BRL\"}";
        String hash = ParametersHashUtil.computeHash(json);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("Should produce identical hash regardless of JSON key ordering")
    void shouldBeInvariantToKeyOrdering() {
        String json1 = "{\"amount\": 150.50, \"recipientId\": \"w100\", \"note\": \"monthly rent\"}";
        String json2 = "{\"note\": \"monthly rent\", \"amount\": 150.50, \"recipientId\": \"w100\"}";

        String hash1 = ParametersHashUtil.computeHash(json1);
        String hash2 = ParametersHashUtil.computeHash(json2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should produce identical hash regardless of whitespace and formatting")
    void shouldBeInvariantToWhitespace() {
        String compactJson = "{\"amount\":50,\"target\":\"savings\"}";
        String formattedJson = """
                {
                   "amount": 50,
                   "target": "savings"
                }
                """;

        String hash1 = ParametersHashUtil.computeHash(compactJson);
        String hash2 = ParametersHashUtil.computeHash(formattedJson);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should omit null fields for canonical normalization")
    void shouldOmitNullFields() {
        String withNull = "{\"amount\": 200, \"description\": null}";
        String withoutNull = "{\"amount\": 200}";

        String hash1 = ParametersHashUtil.computeHash(withNull);
        String hash2 = ParametersHashUtil.computeHash(withoutNull);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should normalize numeric representation (trailing zeroes in decimals)")
    void shouldNormalizeNumericRepresentation() {
        String json1 = "{\"amount\": 100.00}";
        String json2 = "{\"amount\": 100.0}";
        String json3 = "{\"amount\": 100}";

        String hash1 = ParametersHashUtil.computeHash(json1);
        String hash2 = ParametersHashUtil.computeHash(json2);
        String hash3 = ParametersHashUtil.computeHash(json3);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isEqualTo(hash3);
    }

    @Test
    @DisplayName("Should sort nested object keys while preserving array element order")
    void shouldSortNestedObjectsAndPreserveArrayOrder() {
        String json1 = "{\"items\": [\"b\", \"a\"], \"meta\": {\"z\": 1, \"a\": 2}}";
        String json2 = "{\"meta\": {\"a\": 2, \"z\": 1}, \"items\": [\"b\", \"a\"]}";

        String hash1 = ParametersHashUtil.computeHash(json1);
        String hash2 = ParametersHashUtil.computeHash(json2);

        assertThat(hash1).isEqualTo(hash2);

        // Different array order must yield different hash
        String jsonArrayDifferent = "{\"items\": [\"a\", \"b\"], \"meta\": {\"a\": 2, \"z\": 1}}";
        String hash3 = ParametersHashUtil.computeHash(jsonArrayDifferent);
        assertThat(hash1).isNotEqualTo(hash3);
    }

    @Test
    @DisplayName("Should detect parameter modifications")
    void shouldDetectModifications() {
        String original = "{\"amount\": 100.00, \"to\": \"acc-1\"}";
        String tampered = "{\"amount\": 100.01, \"to\": \"acc-1\"}";

        String hash1 = ParametersHashUtil.computeHash(original);
        String hash2 = ParametersHashUtil.computeHash(tampered);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should reject blank or invalid JSON")
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> ParametersHashUtil.computeHash(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParametersHashUtil.computeHash("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParametersHashUtil.computeHash("{invalid json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
