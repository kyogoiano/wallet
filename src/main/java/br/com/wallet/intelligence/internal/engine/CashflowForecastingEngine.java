package br.com.wallet.intelligence.internal.engine;

import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.CashflowStatus;
import br.com.wallet.intelligence.internal.domain.Subscription;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Pure domain mathematical cashflow forecasting and liability expansion engine (REQ-CASH-001, REQ-CASH-002, REQ-CASH-003).
 * Zero dependencies on Spring, database DAOs, REST, or Goals persistence (I-CASH-001, I-CASH-005, I-CASH-008).
 */
@Component
public class CashflowForecastingEngine {

    private static final BigDecimal MULTIPLIER_WEEKLY = new BigDecimal("4.33");
    private static final BigDecimal MULTIPLIER_BI_WEEKLY = new BigDecimal("2.17");
    private static final BigDecimal MULTIPLIER_MONTHLY = new BigDecimal("1.00");
    private static final BigDecimal DIVISOR_ANNUAL = new BigDecimal("12.00");

    public static final String CLASSIFICATION_INSTALLMENT = "INSTALLMENT";

    @NonNull
    public CashflowForecast forecast(
            @NonNull final List<Subscription> activeSubscriptions,
            @NonNull final BigDecimal currentBalance,
            @NonNull final Instant evaluationTime
    ) {
        Objects.requireNonNull(activeSubscriptions, "activeSubscriptions cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");
        Objects.requireNonNull(evaluationTime, "evaluationTime cannot be null");

        BigDecimal liabilities7d = calculateLiabilities(activeSubscriptions, evaluationTime, 7);
        BigDecimal liabilities14d = calculateLiabilities(activeSubscriptions, evaluationTime, 14);
        BigDecimal liabilities30d = calculateLiabilities(activeSubscriptions, evaluationTime, 30);

        BigDecimal shortfall14d = calculateShortfall(liabilities14d, currentBalance);
        BigDecimal shortfall30d = calculateShortfall(liabilities30d, currentBalance);

        CashflowStatus status30d = shortfall30d.compareTo(BigDecimal.ZERO) > 0
                ? CashflowStatus.DEFICIT_WARNING
                : CashflowStatus.SURPLUS;

        BigDecimal normalizedMonthlyCommitted = calculateMonthlyCommittedExpenses(activeSubscriptions);

        int activeInstallments = (int) activeSubscriptions.stream()
                .filter(s -> CLASSIFICATION_INSTALLMENT.equalsIgnoreCase(s.classification()))
                .count();

        return new CashflowForecast(
                liabilities7d,
                liabilities14d,
                liabilities30d,
                shortfall14d,
                shortfall30d,
                status30d,
                normalizedMonthlyCommitted,
                activeInstallments
        );
    }

    @NonNull
    public BigDecimal calculateMonthlyCommittedExpenses(@NonNull final List<Subscription> activeSubscriptions) {
        Objects.requireNonNull(activeSubscriptions, "activeSubscriptions cannot be null");

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        for (Subscription sub : activeSubscriptions) {
            if (CLASSIFICATION_INSTALLMENT.equalsIgnoreCase(sub.classification())) {
                // Finite installments are excluded from perpetual monthly committed expenses (I-CASH-003)
                continue;
            }

            BigDecimal normalized = switch (sub.cadence()) {
                case WEEKLY -> sub.averageAmount().multiply(MULTIPLIER_WEEKLY).setScale(2, RoundingMode.HALF_EVEN);
                case BI_WEEKLY -> sub.averageAmount().multiply(MULTIPLIER_BI_WEEKLY).setScale(2, RoundingMode.HALF_EVEN);
                case MONTHLY -> sub.averageAmount().multiply(MULTIPLIER_MONTHLY).setScale(2, RoundingMode.HALF_EVEN);
                case ANNUAL -> sub.averageAmount().divide(DIVISOR_ANNUAL, 2, RoundingMode.HALF_EVEN);
                case IRREGULAR -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
            };

            total = total.add(normalized);
        }

        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    @NonNull
    private BigDecimal calculateLiabilities(
            @NonNull final List<Subscription> subscriptions,
            @NonNull final Instant evaluationTime,
            final int horizonDays
    ) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        for (Subscription sub : subscriptions) {
            int occurrences = countOccurrences(sub, evaluationTime, horizonDays);
            if (occurrences > 0) {
                BigDecimal subTotal = sub.averageAmount().multiply(BigDecimal.valueOf(occurrences));
                total = total.add(subTotal);
            }
        }

        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    public int countOccurrences(
            @NonNull final Subscription subscription,
            @NonNull final Instant evaluationTime,
            final int horizonDays
    ) {
        if (subscription.cadence() == Cadence.IRREGULAR || subscription.nextExpectedAt() == null) {
            // IRREGULAR cadence produces empty occurrence set O_H = empty (I-CASH-001)
            return 0;
        }

        long intervalDays = switch (subscription.cadence()) {
            case WEEKLY -> 7L;
            case BI_WEEKLY -> 14L;
            case MONTHLY -> 30L;
            case ANNUAL -> 365L;
            case IRREGULAR -> 0L;
        };

        if (intervalDays <= 0) {
            return 0;
        }

        Instant horizonLimit = evaluationTime.plus(horizonDays, ChronoUnit.DAYS);
        Instant occurrence = subscription.nextExpectedAt();

        int count = 0;
        while (!occurrence.isAfter(horizonLimit)) {
            if (!occurrence.isBefore(evaluationTime)) {
                count++;
            }
            occurrence = occurrence.plus(intervalDays, ChronoUnit.DAYS);
        }

        return count;
    }

    @NonNull
    private BigDecimal calculateShortfall(
            @NonNull final BigDecimal liabilities,
            @NonNull final BigDecimal balance
    ) {
        BigDecimal shortfall = liabilities.subtract(balance);
        if (shortfall.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        return shortfall.setScale(2, RoundingMode.HALF_EVEN);
    }
}
