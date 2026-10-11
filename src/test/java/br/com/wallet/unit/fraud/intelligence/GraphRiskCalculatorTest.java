package br.com.wallet.unit.fraud.intelligence;

import br.com.wallet.fraud.intelligence.domain.GraphRiskSignals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphRiskSignals Unit Tests (Composite Scoring & Weight Validation)")
class GraphRiskCalculatorTest {

    @Test
    @DisplayName("Should return 0.0 for zero signals")
    void shouldReturnZeroForZeroSignals() {
        GraphRiskSignals signals = GraphRiskSignals.zero();
        assertThat(signals.calculateCompositeScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate weighted composite score with correct weight distribution")
    void shouldCalculateWeightedCompositeScore() {
        // cycleRisk = 1.0 (weight 0.35)
        // muleHubRisk = 0.50 (weight 0.25 -> 0.125)
        // sharedIdentityRisk = 0.80 (weight 0.20 -> 0.16)
        // fanInRisk = 0.0 (weight 0.10 -> 0.0)
        // fanOutRisk = 0.50 (weight 0.10 -> 0.05)
        // Total expected = 0.35 + 0.125 + 0.16 + 0.0 + 0.05 = 0.685
        GraphRiskSignals signals = new GraphRiskSignals(1.0, 0.0, 0.50, 0.80, 0.50);
        double composite = signals.calculateCompositeScore();
        assertThat(composite).isEqualTo(0.685);
    }

    @Test
    @DisplayName("Should clamp signals and composite score strictly to [0.0, 1.0]")
    void shouldClampScoreToUnitInterval() {
        GraphRiskSignals signals = new GraphRiskSignals(2.0, 1.5, 1.5, 1.2, 1.8);
        double composite = signals.calculateCompositeScore();
        assertThat(composite).isEqualTo(1.0);
    }
}
