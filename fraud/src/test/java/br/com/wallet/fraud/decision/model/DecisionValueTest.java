package br.com.wallet.fraud.decision.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DecisionValue Algebraic Hierarchy Tests")
class DecisionValueTest {

    @Nested
    @DisplayName("BooleanDecision")
    class BooleanDecisionTests {

        @Test
        @DisplayName("should construct boolean decision with rationale")
        void shouldConstructBooleanDecision() {
            BooleanDecision decision = new BooleanDecision(true, "Transaction velocity exceeds 99th percentile");
            assertThat(decision.value()).isTrue();
            assertThat(decision.rationale()).isEqualTo("Transaction velocity exceeds 99th percentile");
        }

        @Test
        @DisplayName("should reject null rationale")
        void shouldRejectNullRationale() {
            assertThatThrownBy(() -> new BooleanDecision(false, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rationale must not be null");
        }
    }

    @Nested
    @DisplayName("ScoreDecision")
    class ScoreDecisionTests {

        @Test
        @DisplayName("should construct valid score decision with normalized scale 2 BigDecimal")
        void shouldConstructValidScoreDecision() {
            ScoreDecision decision = new ScoreDecision(new BigDecimal("0.85"), "High mule ring risk score");
            assertThat(decision.score()).isEqualByComparingTo(new BigDecimal("0.85"));
            assertThat(decision.score().scale()).isEqualTo(2);
            assertThat(decision.rationale()).isEqualTo("High mule ring risk score");
        }

        @Test
        @DisplayName("should accept boundary values 0.00 and 1.00")
        void shouldAcceptBoundaryValues() {
            ScoreDecision minScore = new ScoreDecision(new BigDecimal("0.00"), "Zero risk");
            ScoreDecision maxScore = new ScoreDecision(new BigDecimal("1.00"), "Absolute risk");

            assertThat(minScore.score()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(maxScore.score()).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("should reject null score or rationale")
        void shouldRejectNulls() {
            assertThatThrownBy(() -> new ScoreDecision(null, "Rationale"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("score must not be null");

            assertThatThrownBy(() -> new ScoreDecision(new BigDecimal("0.50"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rationale must not be null");
        }

        @Test
        @DisplayName("should reject score outside [0.00, 1.00] range")
        void shouldRejectOutOfRangeScore() {
            assertThatThrownBy(() -> new ScoreDecision(new BigDecimal("-0.01"), "Negative"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be in range [0.00, 1.00]");

            assertThatThrownBy(() -> new ScoreDecision(new BigDecimal("1.01"), "Excessive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be in range [0.00, 1.00]");
        }
    }

    @Nested
    @DisplayName("CategoryDecision")
    class CategoryDecisionTests {

        @Test
        @DisplayName("should construct valid category decision")
        void shouldConstructCategoryDecision() {
            CategoryDecision decision = new CategoryDecision("HIGH_RISK_MERCHANT", "Known gambling entity");
            assertThat(decision.category()).isEqualTo("HIGH_RISK_MERCHANT");
            assertThat(decision.rationale()).isEqualTo("Known gambling entity");
        }

        @Test
        @DisplayName("should reject null category or rationale")
        void shouldRejectNulls() {
            assertThatThrownBy(() -> new CategoryDecision(null, "Rationale"))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CategoryDecision("CAT", null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("TextDecision")
    class TextDecisionTests {

        @Test
        @DisplayName("should construct text narrative summary")
        void shouldConstructTextDecision() {
            TextDecision decision = new TextDecision("Mule account network detected across 3 accounts.");
            assertThat(decision.summary()).isEqualTo("Mule account network detected across 3 accounts.");
        }

        @Test
        @DisplayName("should reject null summary")
        void shouldRejectNullSummary() {
            assertThatThrownBy(() -> new TextDecision(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("MultiSelectDecision")
    class MultiSelectDecisionTests {

        @Test
        @DisplayName("should construct defensive copy of immutable set")
        void shouldConstructMultiSelectDecision() {
            Set<String> vectors = Set.of("RAPID_DRAIN", "DEVICE_GEO_SPOOF");
            MultiSelectDecision decision = new MultiSelectDecision(vectors, "Multiple risk vectors detected");

            assertThat(decision.selected()).containsExactlyInAnyOrder("RAPID_DRAIN", "DEVICE_GEO_SPOOF");
            assertThat(decision.rationale()).isEqualTo("Multiple risk vectors detected");
        }

        @Test
        @DisplayName("should reject null selected set or rationale")
        void shouldRejectNulls() {
            assertThatThrownBy(() -> new MultiSelectDecision(null, "Rationale"))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new MultiSelectDecision(Set.of("VEC"), null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
