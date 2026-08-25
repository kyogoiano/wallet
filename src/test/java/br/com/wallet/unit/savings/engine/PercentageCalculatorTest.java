package br.com.wallet.unit.savings.engine;

import br.com.wallet.savings.internal.engine.PercentageCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PercentageCalculator Unit Tests")
class PercentageCalculatorTest {

    private final PercentageCalculator calculator = new PercentageCalculator();

    @ParameterizedTest(name = "amount={0}, percentage={1}% -> expected sweep={2}")
    @CsvSource({
            "5000.00, 10.00, 500.00",
            "1000.00, 15.50, 155.00",
            "100.00, 100.00, 100.00",
            "101.01, 10.00, 10.10",    // 10.101 -> HALF_EVEN -> 10.10
            "101.05, 10.00, 10.10",    // 10.105 -> 10.10 (even) or 10.11
            "101.15, 10.00, 10.12",    // 10.115 -> HALF_EVEN (even is 2) -> 10.12
            "0.00, 10.00, 0.00",
            "50.00, 0.50, 0.25"
    })
    @DisplayName("Should correctly calculate percentage sweep with HALF_EVEN rounding")
    void shouldCalculatePercentageSweep(String amountStr, String rateStr, String expectedSweepStr) {
        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal rate = new BigDecimal(rateStr);
        BigDecimal expectedSweep = new BigDecimal(expectedSweepStr);

        BigDecimal actualSweep = calculator.calculate(amount, rate);

        assertThat(actualSweep).isEqualByComparingTo(expectedSweep);
    }

    @Test
    @DisplayName("Should reject invalid percentage rates")
    void shouldRejectInvalidRates() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Percentage rate must be > 0 and <= 100");

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), new BigDecimal("-5.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Percentage rate must be > 0 and <= 100");

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Percentage rate must be > 0 and <= 100");
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThatThrownBy(() -> calculator.calculate(null, new BigDecimal("10.00")))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
