package br.com.wallet.decision.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DecisionEvidence Machine-Verifiable Grounding & Hashing Tests (I-TYPED-004)")
class DecisionEvidenceTest {

    @Test
    @DisplayName("should compute identical content hash regardless of input map key insertion order")
    void shouldProduceDeterministicHashAcrossKeyOrders() {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("zeta", 100);
        map1.put("alpha", "active");
        map1.put("beta", true);

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("alpha", "active");
        map2.put("beta", true);
        map2.put("zeta", 100);

        DecisionEvidence ev1 = DecisionEvidence.of("user_profile", "User demographic profile", map1);
        DecisionEvidence ev2 = DecisionEvidence.of("user_profile", "User demographic profile", map2);

        assertThat(ev1.contentHash()).isNotBlank().hasSize(64); // SHA-256 hex length
        assertThat(ev1.contentHash()).isEqualTo(ev2.contentHash());
    }

    @Test
    @DisplayName("should alter content hash when facts are modified")
    void shouldDetectFactModificationInHash() {
        DecisionEvidence evOriginal = DecisionEvidence.of("tx_facts", "Summary", Map.of("amount", "100.00"));
        DecisionEvidence evTampered = DecisionEvidence.of("tx_facts", "Summary", Map.of("amount", "100.01"));

        assertThat(evOriginal.contentHash()).isNotEqualTo(evTampered.contentHash());
    }

    @Test
    @DisplayName("should reject null inputs")
    void shouldRejectNullInputs() {
        assertThatThrownBy(() -> DecisionEvidence.of(null, "Summary", Map.of()))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> DecisionEvidence.of("key", null, Map.of()))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> DecisionEvidence.of("key", "Summary", null))
            .isInstanceOf(NullPointerException.class);
    }
}
