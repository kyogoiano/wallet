package br.com.wallet.unit.savings.engine;

import br.com.wallet.savings.internal.engine.RoundUpCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RoundUpCalculator Unit Tests")
class RoundUpCalculatorTest {

    private final RoundUpCalculator calculator = new RoundUpCalculator();

    @ParameterizedTest(name = "amount={0}, step={1} -> expected sweep={2}")
    @CsvSource({
            "47.30, 5.00, 2.70",
            "47.30, 1.00, 0.70",
            "47.30, 10.00, 2.70",
            "47.30, 50.00, 2.70",
            "50.00, 5.00, 0.00",
            "50.00, 10.00, 0.00",
            "0.01, 1.00, 0.99",
            "99.99, 100.00, 0.01",
            "100.00, 100.00, 0.00",
            "12.34, 0.50, 0.16"
    })
    @DisplayName("Should correctly calculate round-up micro-savings delta")
    void shouldCalculateRoundUpDelta(String amountStr, String stepStr, String expectedSweepStr) {
        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal step = new BigDecimal(stepStr);
        BigDecimal expectedSweep = new BigDecimal(expectedSweepStr);

        BigDecimal actualSweep = calculator.calculate(amount, step);

        assertThat(actualSweep).isEqualByComparingTo(expectedSweep);
    }

    @Test
    @DisplayName("Should reject non-positive step amounts")
    void shouldRejectNonPositiveStep() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("10.00"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Step amount must be strictly positive");

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("10.00"), new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Step amount must be strictly positive");
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThatThrownBy(() -> calculator.calculate(null, new BigDecimal("1.00")))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("10.00"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
