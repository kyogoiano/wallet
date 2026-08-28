package br.com.wallet.goals.internal.engine;

import br.com.wallet.goals.api.model.CashflowProfile;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class CashflowCapacityCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    @NonNull
    public BigDecimal calculateSafeMonthlyCapacity(@Nullable final CashflowProfile profile) {
        if (profile == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }

        final BigDecimal netDisposable = profile.monthlyIncome()
                .subtract(profile.monthlyCommittedExpenses())
                .max(BigDecimal.ZERO);

        final BigDecimal safeCapacity = netDisposable
                .subtract(profile.minimumSafetyBuffer())
                .max(BigDecimal.ZERO);

        return safeCapacity.setScale(SCALE, ROUNDING_MODE);
    }

    @NonNull
    public BigDecimal calculateRecommendedContribution(
            @NonNull final BigDecimal requiredMonthlyContribution,
            @NonNull final BigDecimal safeMonthlyCapacity
    ) {
        Objects.requireNonNull(requiredMonthlyContribution, "requiredMonthlyContribution cannot be null");
        Objects.requireNonNull(safeMonthlyCapacity, "safeMonthlyCapacity cannot be null");

        if (requiredMonthlyContribution.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }

        return requiredMonthlyContribution.min(safeMonthlyCapacity).setScale(SCALE, ROUNDING_MODE);
    }
}
