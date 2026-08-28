package br.com.wallet.unit.goals.engine;

import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.goals.internal.engine.CashflowCapacityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit Tests: CashflowCapacityCalculator")
class CashflowCapacityCalculatorTest {

    private CashflowCapacityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CashflowCapacityCalculator();
    }

    @Test
    @DisplayName("Should calculate safe monthly capacity respecting committed expenses and safety buffer (I-GOAL-002)")
    void shouldCalculateSafeMonthlyCapacity() {
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"), // Income
                new BigDecimal("6000.00"),  // Expenses
                new BigDecimal("2000.00"),  // Safety buffer
                Instant.now()
        );

        // Net = 10000 - 6000 = 4000; Safe = 4000 - 2000 = 2000
        BigDecimal capacity = calculator.calculateSafeMonthlyCapacity(profile);

        assertThat(capacity).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Should return zero capacity when expenses plus buffer exceed income")
    void shouldReturnZeroCapacityWhenExpensesExceedIncome() {
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("5000.00"), // Income
                new BigDecimal("4500.00"), // Expenses
                new BigDecimal("1000.00"), // Safety buffer (Total 5500 > 5000)
                Instant.now()
        );

        BigDecimal capacity = calculator.calculateSafeMonthlyCapacity(profile);

        assertThat(capacity).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Should calculate recommended contribution as minimum of required and safe capacity")
    void shouldCalculateRecommendedContribution() {
        BigDecimal required = new BigDecimal("1500.00");
        BigDecimal safeCapacity = new BigDecimal("2000.00");

        BigDecimal recommended = calculator.calculateRecommendedContribution(required, safeCapacity);

        assertThat(recommended).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Should cap recommended contribution to safe capacity when required is higher")
    void shouldCapRecommendedContributionToSafeCapacity() {
        BigDecimal required = new BigDecimal("3000.00");
        BigDecimal safeCapacity = new BigDecimal("2000.00");

        BigDecimal recommended = calculator.calculateRecommendedContribution(required, safeCapacity);

        assertThat(recommended).isEqualByComparingTo("2000.00");
    }
}
