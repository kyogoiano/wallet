package br.com.wallet.unit.goals.engine;

import br.com.wallet.goals.internal.engine.ContributionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit Tests: ContributionCalculator")
class ContributionCalculatorTest {

    private ContributionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ContributionCalculator();
    }

    @Test
    @DisplayName("Should calculate remaining deficit correctly when balance is below target")
    void shouldCalculateRemainingDeficit() {
        BigDecimal target = new BigDecimal("10000.00");
        BigDecimal current = new BigDecimal("2500.00");

        BigDecimal deficit = calculator.calculateDeficit(target, current);

        assertThat(deficit).isEqualByComparingTo("7500.00");
    }

    @Test
    @DisplayName("Should return zero deficit when current balance meets or exceeds target")
    void shouldReturnZeroDeficitWhenTargetMet() {
        BigDecimal target = new BigDecimal("10000.00");
        BigDecimal current = new BigDecimal("12000.00");

        BigDecimal deficit = calculator.calculateDeficit(target, current);

        assertThat(deficit).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Should calculate required monthly contribution with HALF_EVEN rounding")
    void shouldCalculateRequiredMonthlyContribution() {
        BigDecimal deficit = new BigDecimal("10000.00");
        long remainingMonths = 3; // 10000 / 3 = 3333.3333... -> 3333.33

        BigDecimal required = calculator.calculateRequiredMonthlyContribution(deficit, remainingMonths);

        assertThat(required).isEqualByComparingTo("3333.33");
    }

    @Test
    @DisplayName("Should calculate remaining months between evaluation date and target date")
    void shouldCalculateRemainingMonths() {
        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);
        LocalDate targetDate = LocalDate.of(2027, 8, 27);

        long months = calculator.calculateRemainingMonths(evaluationDate, targetDate);

        assertThat(months).isEqualTo(12);
    }

    @Test
    @DisplayName("Should return at least 1 remaining month if target date is in the same month")
    void shouldReturnAtLeastOneMonthWhenTargetIsInSameMonth() {
        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);
        LocalDate targetDate = LocalDate.of(2026, 8, 30);

        long months = calculator.calculateRemainingMonths(evaluationDate, targetDate);

        assertThat(months).isEqualTo(1);
    }

    @Test
    @DisplayName("Should calculate projected completion date based on safe capacity")
    void shouldCalculateProjectedCompletionDate() {
        BigDecimal deficit = new BigDecimal("5000.00");
        BigDecimal capacity = new BigDecimal("1000.00");
        LocalDate evaluationDate = LocalDate.of(2026, 8, 1);

        LocalDate projectedDate = calculator.calculateProjectedCompletionDate(deficit, capacity, evaluationDate);

        // 5000 / 1000 = 5 months -> 2026-08-01 + 5 months = 2027-01-01
        assertThat(projectedDate).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("Should return null projected completion date when capacity is zero")
    void shouldReturnNullProjectedDateWhenCapacityIsZero() {
        BigDecimal deficit = new BigDecimal("5000.00");
        BigDecimal capacity = BigDecimal.ZERO;
        LocalDate evaluationDate = LocalDate.of(2026, 8, 1);

        LocalDate projectedDate = calculator.calculateProjectedCompletionDate(deficit, capacity, evaluationDate);

        assertThat(projectedDate).isNull();
    }
}
