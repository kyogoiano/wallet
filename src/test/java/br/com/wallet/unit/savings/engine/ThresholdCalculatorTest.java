package br.com.wallet.unit.savings.engine;

import br.com.wallet.savings.internal.engine.ThresholdCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ThresholdCalculator Unit Tests")
class ThresholdCalculatorTest {

    private final ThresholdCalculator calculator = new ThresholdCalculator();

    @ParameterizedTest(name = "balance={0}, ceiling={1} -> expected sweep={2}")
    @CsvSource({
            "12500.00, 10000.00, 2500.00",
            "10000.00, 10000.00, 0.00",
            "8000.00, 10000.00, 0.00",
            "0.00, 1000.00, 0.00",
            "1000.50, 1000.00, 0.50"
    })
    @DisplayName("Should correctly calculate threshold excess sweep")
    void shouldCalculateThresholdSweep(String balanceStr, String ceilingStr, String expectedSweepStr) {
        BigDecimal balance = new BigDecimal(balanceStr);
        BigDecimal ceiling = new BigDecimal(ceilingStr);
        BigDecimal expectedSweep = new BigDecimal(expectedSweepStr);

        BigDecimal actualSweep = calculator.calculate(balance, ceiling);

        assertThat(actualSweep).isEqualByComparingTo(expectedSweep);
    }

    @Test
    @DisplayName("Should reject non-positive ceiling thresholds")
    void shouldRejectNonPositiveCeiling() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ceiling threshold must be strictly positive");

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), new BigDecimal("-50.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ceiling threshold must be strictly positive");
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThatThrownBy(() -> calculator.calculate(null, new BigDecimal("1000.00")))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("1000.00"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
