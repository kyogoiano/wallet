package br.com.wallet.decision.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DecisionOutcome Sealed Hierarchy & Structural Absence Tests")
class DecisionOutcomeTest {

    @Test
    @DisplayName("should construct valid DecisionAnswer with confidence, grounding, and provenance")
    void shouldConstructDecisionAnswer() {
        BooleanDecision value = new BooleanDecision(true, "Velocity surge");
        DecisionProvenance provenance = new DecisionProvenance("qwen2.5:7b-instruct-q4_K_M", "v1.2", Instant.now(), 45L);
        DecisionEvidence evidence = new DecisionEvidence("tx_history", "Recent transaction counts", java.util.Map.of("count", 12), "abc123hash");

        DecisionAnswer<BooleanDecision> answer = new DecisionAnswer<>(
            value,
            Confidence.HIGH,
            List.of(evidence),
            provenance
        );

        assertThat(answer.value()).isEqualTo(value);
        assertThat(answer.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(answer.grounding()).containsExactly(evidence);
        assertThat(answer.provenance()).isEqualTo(provenance);
    }

    @Test
    @DisplayName("should construct DecisionUnavailable with reason and provenance, without synthetic score")
    void shouldConstructDecisionUnavailable() {
        DecisionProvenance provenance = new DecisionProvenance("qwen2.5:7b", "v1.0", Instant.now(), 5000L);

        DecisionUnavailable<ScoreDecision> unavailable = new DecisionUnavailable<>(
            UnavailableReason.TIMEOUT,
            "Evaluator timed out after 5000ms",
            provenance
        );

        assertThat(unavailable.reason()).isEqualTo(UnavailableReason.TIMEOUT);
        assertThat(unavailable.diagnosticMessage()).isEqualTo("Evaluator timed out after 5000ms");
        assertThat(unavailable.provenance()).isEqualTo(provenance);
    }

    @Test
    @DisplayName("STRUCTURAL INVARIANT: DecisionUnavailable must NOT expose score, value, or confidence methods")
    void decisionUnavailableMustNotExposeScoreOrConfidence() {
        List<String> methodNames = Arrays.stream(DecisionUnavailable.class.getMethods())
            .map(Method::getName)
            .toList();

        assertThat(methodNames)
            .doesNotContain("score", "value", "confidence", "getScore", "getValue", "getConfidence");
    }

    @Test
    @DisplayName("should reject null mandatory fields in DecisionAnswer and DecisionUnavailable")
    void shouldRejectNulls() {
        DecisionProvenance prov = new DecisionProvenance("m", "v1", Instant.now(), 10L);

        assertThatThrownBy(() -> new DecisionAnswer<>(null, Confidence.HIGH, List.of(), prov))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DecisionAnswer<>(new BooleanDecision(true, "ok"), null, List.of(), prov))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DecisionUnavailable<>(null, "err", prov))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DecisionUnavailable<>(UnavailableReason.TIMEOUT, null, prov))
            .isInstanceOf(NullPointerException.class);
    }
}
