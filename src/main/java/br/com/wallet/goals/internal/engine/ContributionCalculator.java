package br.com.wallet.goals.internal.engine;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Component
public class ContributionCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    @NonNull
    public BigDecimal calculateDeficit(
            @NonNull final BigDecimal targetAmount,
            @NonNull final BigDecimal currentBalance
    ) {
        Objects.requireNonNull(targetAmount, "targetAmount cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");

        final BigDecimal deficit = targetAmount.subtract(currentBalance);
        return deficit.max(BigDecimal.ZERO).setScale(SCALE, ROUNDING_MODE);
    }

    public long calculateRemainingMonths(
            @NonNull final LocalDate evaluationDate,
            @NonNull final LocalDate targetDate
    ) {
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");
        Objects.requireNonNull(targetDate, "targetDate cannot be null");

        final long months = ChronoUnit.MONTHS.between(evaluationDate, targetDate);
        return Math.max(1, months);
    }

    @NonNull
    public BigDecimal calculateRequiredMonthlyContribution(
            @NonNull final BigDecimal deficit,
            final long remainingMonths
    ) {
        Objects.requireNonNull(deficit, "deficit cannot be null");

        if (deficit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }

        final long effectiveMonths = Math.max(1, remainingMonths);
        return deficit.divide(BigDecimal.valueOf(effectiveMonths), SCALE, ROUNDING_MODE);
    }

    @Nullable
    public LocalDate calculateProjectedCompletionDate(
            @NonNull final BigDecimal deficit,
            @NonNull final BigDecimal monthlyCapacity,
            @NonNull final LocalDate evaluationDate
    ) {
        Objects.requireNonNull(deficit, "deficit cannot be null");
        Objects.requireNonNull(monthlyCapacity, "monthlyCapacity cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");

        if (deficit.compareTo(BigDecimal.ZERO) <= 0) {
            return evaluationDate;
        }

        if (monthlyCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // Ceiling division: ceil(deficit / monthlyCapacity)
        final BigDecimal monthsRequired = deficit.divide(monthlyCapacity, 0, RoundingMode.CEILING);
        return evaluationDate.plusMonths(monthsRequired.longValue());
    }
}
