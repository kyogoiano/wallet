package br.com.wallet.intelligence.internal.engine;

import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.CashflowStatus;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CashflowForecastingEngineTest {

    private CashflowForecastingEngine engine;
    private Instant t0;

    @BeforeEach
    void setUp() {
        engine = new CashflowForecastingEngine();
        t0 = Instant.parse("2026-10-01T00:00:00Z");
    }

    private Subscription createSubscription(
            Cadence cadence,
            BigDecimal amount,
            String classification,
            Instant nextExpectedAt
    ) {
        return new Subscription(
                UUID.randomUUID(),
                "tenant-alpha",
                UUID.randomUUID(),
                UUID.randomUUID(),
                cadence,
                SubscriptionStatus.ACTIVE,
                PriceState.NORMAL,
                classification,
                amount,
                amount,
                BigDecimal.valueOf(1.0),
                3,
                VarianceType.FIXED,
                nextExpectedAt,
                t0.minus(30, ChronoUnit.DAYS),
                t0.minus(90, ChronoUnit.DAYS),
                t0
        );
    }

    @Nested
    @DisplayName("Occurrence Expansion & Forward Liabilities (TASK-3.2.3, I-CASH-001, I-CASH-005)")
    class OccurrenceExpansionTests {

        @Test
        @DisplayName("Weekly subscription of R$ 50.00 expands to 1, 2, and 4 occurrences across 7, 14, 30 days")
        void weeklyOccurrencesAcrossHorizons() {
            // nextExpectedAt = t0 + 7 days (2026-10-08)
            Subscription weekly = createSubscription(
                    Cadence.WEEKLY,
                    new BigDecimal("50.00"),
                    "SUBSCRIPTION",
                    t0.plus(7, ChronoUnit.DAYS)
            );

            CashflowForecast forecast = engine.forecast(List.of(weekly), new BigDecimal("500.00"), t0);

            // 7d: 1 occurrence (Oct 8) -> 50.00
            assertThat(forecast.liabilities7Days()).isEqualByComparingTo("50.00");
            // 14d: 2 occurrences (Oct 8, Oct 15) -> 100.00
            assertThat(forecast.liabilities14Days()).isEqualByComparingTo("100.00");
            // 30d: 4 occurrences (Oct 8, Oct 15, Oct 22, Oct 29) -> 200.00
            assertThat(forecast.liabilities30Days()).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("Bi-weekly subscription of R$ 100.00 expands to 0, 1, and 2 occurrences across 7, 14, 30 days")
        void biWeeklyOccurrencesAcrossHorizons() {
            // nextExpectedAt = t0 + 14 days (2026-10-15)
            Subscription biWeekly = createSubscription(
                    Cadence.BI_WEEKLY,
                    new BigDecimal("100.00"),
                    "SUBSCRIPTION",
                    t0.plus(14, ChronoUnit.DAYS)
            );

            CashflowForecast forecast = engine.forecast(List.of(biWeekly), new BigDecimal("500.00"), t0);

            assertThat(forecast.liabilities7Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.liabilities14Days()).isEqualByComparingTo("100.00");
            assertThat(forecast.liabilities30Days()).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("Monthly subscription of R$ 150.00 expands to 0, 0, and 1 occurrence across 7, 14, 30 days")
        void monthlyOccurrencesAcrossHorizons() {
            // nextExpectedAt = t0 + 30 days (2026-10-31)
            Subscription monthly = createSubscription(
                    Cadence.MONTHLY,
                    new BigDecimal("150.00"),
                    "SUBSCRIPTION",
                    t0.plus(30, ChronoUnit.DAYS)
            );

            CashflowForecast forecast = engine.forecast(List.of(monthly), new BigDecimal("500.00"), t0);

            assertThat(forecast.liabilities7Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.liabilities14Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.liabilities30Days()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("Irregular cadence produces empty occurrences and zero liability")
        void irregularProducesZeroLiability() {
            Subscription irregular = createSubscription(
                    Cadence.IRREGULAR,
                    new BigDecimal("200.00"),
                    "UNKNOWN",
                    null
            );

            CashflowForecast forecast = engine.forecast(List.of(irregular), new BigDecimal("500.00"), t0);

            assertThat(forecast.liabilities7Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.liabilities14Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.liabilities30Days()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Deterministic execution: identical inputs and fixed t0 produce identical forecast")
        void deterministicExecution() {
            Subscription s1 = createSubscription(Cadence.WEEKLY, new BigDecimal("25.00"), "SUBSCRIPTION", t0.plus(7, ChronoUnit.DAYS));
            Subscription s2 = createSubscription(Cadence.MONTHLY, new BigDecimal("100.00"), "SUBSCRIPTION", t0.plus(20, ChronoUnit.DAYS));

            CashflowForecast f1 = engine.forecast(List.of(s1, s2), new BigDecimal("200.00"), t0);
            CashflowForecast f2 = engine.forecast(List.of(s1, s2), new BigDecimal("200.00"), t0);

            assertThat(f1).isEqualTo(f2);
        }
    }

    @Nested
    @DisplayName("Current-Balance Coverage Shortfall Evaluation (TASK-3.2.4, I-CASH-002)")
    class ShortfallEvaluationTests {

        @Test
        @DisplayName("Deficit warning when forward liabilities exceed current balance")
        void deficitWarningWhenLiabilitiesExceedBalance() {
            Subscription weekly = createSubscription(Cadence.WEEKLY, new BigDecimal("50.00"), "SUBSCRIPTION", t0.plus(7, ChronoUnit.DAYS));
            // Liabilities: 14d = 100.00, 30d = 200.00. Current Balance = 150.00
            CashflowForecast forecast = engine.forecast(List.of(weekly), new BigDecimal("150.00"), t0);

            // Shortfall14d = max(0, 100 - 150) = 0.00
            assertThat(forecast.shortfall14Days()).isEqualByComparingTo("0.00");
            // Shortfall30d = max(0, 200 - 150) = 50.00
            assertThat(forecast.shortfall30Days()).isEqualByComparingTo("50.00");
            assertThat(forecast.status30Days()).isEqualTo(CashflowStatus.DEFICIT_WARNING);
        }

        @Test
        @DisplayName("Surplus status when current balance exceeds forward liabilities")
        void surplusWhenBalanceExceedsLiabilities() {
            Subscription weekly = createSubscription(Cadence.WEEKLY, new BigDecimal("50.00"), "SUBSCRIPTION", t0.plus(7, ChronoUnit.DAYS));
            // Liabilities: 30d = 200.00. Current Balance = 300.00
            CashflowForecast forecast = engine.forecast(List.of(weekly), new BigDecimal("300.00"), t0);

            assertThat(forecast.shortfall14Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.shortfall30Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.status30Days()).isEqualTo(CashflowStatus.SURPLUS);
        }

        @Test
        @DisplayName("Zero or negative balance results in shortfall equal to total liability")
        void negativeBalanceHandling() {
            Subscription weekly = createSubscription(Cadence.WEEKLY, new BigDecimal("50.00"), "SUBSCRIPTION", t0.plus(7, ChronoUnit.DAYS));
            CashflowForecast forecast = engine.forecast(List.of(weekly), new BigDecimal("-10.00"), t0);

            // Liabilities 14d = 100.00, - (-10.00) = 110.00
            assertThat(forecast.shortfall14Days()).isEqualByComparingTo("110.00");
            // Liabilities 30d = 200.00, - (-10.00) = 210.00
            assertThat(forecast.shortfall30Days()).isEqualByComparingTo("210.00");
            assertThat(forecast.status30Days()).isEqualTo(CashflowStatus.DEFICIT_WARNING);
        }
    }

    @Nested
    @DisplayName("Monthly Committed Normalization & Installment Partitioning (TASK-3.2.5, I-CASH-003)")
    class MonthlyCommittedAndInstallmentsTests {

        @Test
        @DisplayName("Installment is excluded from monthly committed expenses but included in forward liabilities")
        void installmentExcludedFromMonthlyCommitted() {
            // Subscription: monthly R$ 40.00
            Subscription sub = createSubscription(Cadence.MONTHLY, new BigDecimal("40.00"), "SUBSCRIPTION", t0.plus(10, ChronoUnit.DAYS));
            // Installment: bi-weekly R$ 50.00
            Subscription inst = createSubscription(Cadence.BI_WEEKLY, new BigDecimal("50.00"), "INSTALLMENT", t0.plus(14, ChronoUnit.DAYS));

            CashflowForecast forecast = engine.forecast(List.of(sub, inst), new BigDecimal("500.00"), t0);

            // Forward liabilities include installment:
            // sub 30d = 40.00 (1 occurrence), inst 30d = 100.00 (2 occurrences) -> total 140.00
            assertThat(forecast.liabilities30Days()).isEqualByComparingTo("140.00");
            assertThat(forecast.activeInstallmentsCount()).isEqualTo(1);

            // Monthly committed expenses ONLY includes non-installment: 40.00 * 1.00 = 40.00
            assertThat(forecast.normalizedMonthlyCommitted()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("Anti-double-counting: shortfall is calculated exclusively from L_H, not L_H + committed")
        void antiDoubleCountingShortfallRule() {
            // Monthly subscription R$ 100.00, balance R$ 120.00
            Subscription sub = createSubscription(Cadence.MONTHLY, new BigDecimal("100.00"), "SUBSCRIPTION", t0.plus(15, ChronoUnit.DAYS));

            CashflowForecast forecast = engine.forecast(List.of(sub), new BigDecimal("120.00"), t0);

            // L30 = 100.00, Balance = 120.00 -> Shortfall = 0.00 (SURPLUS)
            // If buggy implementation added monthlyCommitted (100.00) to L30 (100.00), sum would be 200.00 -> Shortfall 80.00 (DEFICIT_WARNING)!
            assertThat(forecast.liabilities30Days()).isEqualByComparingTo("100.00");
            assertThat(forecast.normalizedMonthlyCommitted()).isEqualByComparingTo("100.00");
            assertThat(forecast.shortfall30Days()).isEqualByComparingTo("0.00");
            assertThat(forecast.status30Days()).isEqualTo(CashflowStatus.SURPLUS);
        }

        @Test
        @DisplayName("All cadence normalizations match exact multipliers with scale 2 HALF_EVEN")
        void allCadenceNormalizations() {
            Subscription weekly = createSubscription(Cadence.WEEKLY, new BigDecimal("10.00"), "SUBSCRIPTION", t0.plus(7, ChronoUnit.DAYS));
            Subscription biWeekly = createSubscription(Cadence.BI_WEEKLY, new BigDecimal("20.00"), "SUBSCRIPTION", t0.plus(14, ChronoUnit.DAYS));
            Subscription monthly = createSubscription(Cadence.MONTHLY, new BigDecimal("30.00"), "SUBSCRIPTION", t0.plus(30, ChronoUnit.DAYS));
            Subscription annual = createSubscription(Cadence.ANNUAL, new BigDecimal("120.00"), "SUBSCRIPTION", t0.plus(30, ChronoUnit.DAYS));

            // Weekly: 10 * 4.33 = 43.30
            // Bi-weekly: 20 * 2.17 = 43.40
            // Monthly: 30 * 1.00 = 30.00
            // Annual: 120 / 12.00 = 10.00
            // Total = 43.30 + 43.40 + 30.00 + 10.00 = 126.70
            BigDecimal totalCommitted = engine.calculateMonthlyCommittedExpenses(List.of(weekly, biWeekly, monthly, annual));
            assertThat(totalCommitted).isEqualByComparingTo("126.70");
        }
    }
}
