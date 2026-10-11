package br.com.wallet.unit.fraud.fusion;

import br.com.wallet.fraud.fusion.api.RiskFusionEngine;
import br.com.wallet.fraud.fusion.api.model.FactorContribution;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.api.model.RiskFusionWeights;
import br.com.wallet.fraud.fusion.internal.fusion.ProbabilisticRiskFusionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RiskFusionEngine Unit Tests (REQ-FUSION-001, REQ-FUSION-010, I-FUSION-001, I-FUSION-002, I-FUSION-003, I-FUSION-010)")
class RiskFusionEngineTest {

    private RiskFusionEngine engine;
    private RiskFusionWeights defaultWeights;

    @BeforeEach
    void setUp() {
        engine = new ProbabilisticRiskFusionEngine();
        defaultWeights = RiskFusionWeights.defaults();
    }

    @Test
    @DisplayName("I-FUSION-001: Direct Rule Primacy Override — If directRisk >= 1.0, short-circuit with HARD_BLOCK")
    void shouldOverrideWithHardBlockWhenDirectViolated() {
        FraudSignalSet signals = FraudSignalSet.of(1.0, 0.2, 0.3, 0.1, 0.4);
        RiskFusionResult result = engine.fuse(signals, defaultWeights);

        assertThat(result.finalRisk()).isEqualTo(1.0);
        assertThat(result.decision()).isEqualTo(FraudDecision.HARD_BLOCK);
        assertThat(result.attribution().primaryDriver()).isEqualTo("DIRECT_HARD_RULE");
        assertThat(result.attribution().contributions()).hasSize(1);
        assertThat(result.attribution().contributions().getFirst().normalizedPercentage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("REQ-FUSION-001: Multi-Signal Fusion with Correlation Groups — Fuses graph group and independent signals")
    void shouldFuseSignalsWithCorrelationGroups() {
        // signals: direct=0.20, graph=0.80, prop=0.50, beh=0.30, ml=0.40
        // weights: graph=0.85, prop=0.75, beh=0.70, ml=0.60
        // Step 1: Graph group:
        // R_gg = 1 - (1 - 0.80) * (1 - 0.75 * 0.50) = 1 - 0.20 * 0.625 = 1 - 0.125 = 0.875
        // Step 2: Master fusion:
        // (1 - 0.20) * (1 - 0.85 * 0.875) * (1 - 0.70 * 0.30) * (1 - 0.60 * 0.40)
        // = 0.80 * (1 - 0.74375) * (1 - 0.21) * (1 - 0.24)
        // = 0.80 * 0.25625 * 0.79 * 0.76 = 0.80 * 0.1538525 = 0.123082
        // R_final = 1 - 0.123082 = 0.876918
        FraudSignalSet signals = FraudSignalSet.of(0.20, 0.80, 0.50, 0.30, 0.40);
        RiskFusionResult result = engine.fuse(signals, defaultWeights);

        assertThat(result.finalRisk()).isCloseTo(0.8769, within(0.001));
        assertThat(result.decision()).isEqualTo(FraudDecision.RESTRICT);
    }

    @Test
    @DisplayName("I-FUSION-010: Observable ML Degradation — Fusion degrades gracefully when ML is unavailable without synthetic proxy")
    void shouldGracefullyDegradeWhenMlUnavailable() {
        // Without ML factor:
        // (1 - 0.20) * (1 - 0.85 * 0.875) * (1 - 0.70 * 0.30)
        // = 0.80 * 0.25625 * 0.79 = 0.16195
        // R_final = 1 - 0.16195 = 0.83805
        FraudSignalSet signals = FraudSignalSet.withoutMl(0.20, 0.80, 0.50, 0.30, "ONNX_MODEL_UNAVAILABLE");
        RiskFusionResult result = engine.fuse(signals, defaultWeights);

        assertThat(result.finalRisk()).isCloseTo(0.8380, within(0.001));
        assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(result.attribution().degradedReason()).contains("ONNX_MODEL_UNAVAILABLE");
    }

    @Test
    @DisplayName("I-FUSION-002: Monotonic Correlated Bounding — All zeros produce 0.0; scores stay bounded in [0.0, 1.0]")
    void shouldEnforceMonotonicBounds() {
        FraudSignalSet zeroSignals = FraudSignalSet.of(0.0, 0.0, 0.0, 0.0, 0.0);
        RiskFusionResult zeroResult = engine.fuse(zeroSignals, defaultWeights);
        assertThat(zeroResult.finalRisk()).isEqualTo(0.0);
        assertThat(zeroResult.decision()).isEqualTo(FraudDecision.ALLOW);

        FraudSignalSet maxSignals = FraudSignalSet.of(0.99, 1.0, 1.0, 1.0, 1.0);
        RiskFusionResult maxResult = engine.fuse(maxSignals, defaultWeights);
        assertThat(maxResult.finalRisk()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("REQ-FUSION-010 & I-FUSION-003: Leave-One-Out Marginal Attribution — Sum of normalized contributions equals 100%")
    void shouldComputeLeaveOneOutMarginalAttribution() {
        FraudSignalSet signals = FraudSignalSet.of(0.10, 0.90, 0.20, 0.75, 0.30);
        RiskFusionResult result = engine.fuse(signals, defaultWeights);

        assertThat(result.attribution().contributions()).isNotEmpty();

        double sumPercentages = result.attribution().contributions().stream()
            .mapToDouble(FactorContribution::normalizedPercentage)
            .sum();
        assertThat(sumPercentages).isCloseTo(100.0, within(1e-6));

        // Graph intelligence has the highest marginal impact in this scenario
        assertThat(result.attribution().primaryDriver()).isEqualTo("GRAPH_INTELLIGENCE");
    }

    @Test
    @DisplayName("Validation: Signal scores outside [0.0, 1.0] must be rejected with IllegalArgumentException")
    void shouldRejectOutOfBoundsScores() {
        assertThatThrownBy(() -> FraudSignalSet.of(-0.01, 0.5, 0.5, 0.5, 0.5))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> FraudSignalSet.of(0.5, 1.01, 0.5, 0.5, 0.5))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MlRiskResult.Available(1.5, "v1", 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
