package br.com.wallet.unit.edge;

import br.com.wallet.edge.internal.journal.SpoolAdmissionState;
import br.com.wallet.edge.internal.journal.SpoolWatermarkGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpoolWatermarkGate Hysteresis Unit Tests (TASK-2.3, I-EDGE-005)")
class SpoolWatermarkGateTest {

    private SpoolWatermarkGate gate;

    @BeforeEach
    void setUp() {
        gate = new SpoolWatermarkGate();
    }

    @Test
    @DisplayName("Should evaluate NORMAL when usage is under 70%")
    void shouldReportNormalUnder70() {
        assertThat(gate.evaluate(50.0)).isEqualTo(SpoolAdmissionState.NORMAL);
        assertThat(gate.isDegradedAcceptanceAllowed()).isTrue();
    }

    @Test
    @DisplayName("Should evaluate WARNING between 70% and 80%")
    void shouldReportWarningBetween70And80() {
        assertThat(gate.evaluate(75.0)).isEqualTo(SpoolAdmissionState.WARNING);
        assertThat(gate.isDegradedAcceptanceAllowed()).isTrue();
    }

    @Test
    @DisplayName("Should evaluate PRESSURE between 80% and 95%")
    void shouldReportPressureBetween80And95() {
        assertThat(gate.evaluate(88.0)).isEqualTo(SpoolAdmissionState.PRESSURE);
        assertThat(gate.isDegradedAcceptanceAllowed()).isTrue();
    }

    @Test
    @DisplayName("Should transition to SATURATED and reject degraded acceptance at or above 95%")
    void shouldTransitionToSaturatedAt95() {
        assertThat(gate.evaluate(95.0)).isEqualTo(SpoolAdmissionState.SATURATED);
        assertThat(gate.isDegradedAcceptanceAllowed()).isFalse();
        assertThat(gate.retryAfterSeconds()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should maintain SATURATED state between 85% and 95% due to hysteresis (anti-oscillation)")
    void shouldMaintainSaturatedUntilBelow85() {
        // First saturate
        gate.evaluate(96.0);
        assertThat(gate.isDegradedAcceptanceAllowed()).isFalse();

        // Dropping to 90% must STILL remain SATURATED
        assertThat(gate.evaluate(90.0)).isEqualTo(SpoolAdmissionState.SATURATED);
        assertThat(gate.isDegradedAcceptanceAllowed()).isFalse();

        // Dropping to 86% must STILL remain SATURATED
        assertThat(gate.evaluate(86.0)).isEqualTo(SpoolAdmissionState.SATURATED);
        assertThat(gate.isDegradedAcceptanceAllowed()).isFalse();

        // Dropping below 85% recovers!
        assertThat(gate.evaluate(84.9)).isEqualTo(SpoolAdmissionState.PRESSURE);
        assertThat(gate.isDegradedAcceptanceAllowed()).isTrue();
    }
}
