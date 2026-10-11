package br.com.wallet.goals.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record GoalStrategy(
        @Nullable UUID goalId,
        @NonNull BigDecimal targetAmount,
        @NonNull BigDecimal currentAccumulatedAmount,
        @NonNull BigDecimal remainingDeficit,
        long remainingMonths,
        @NonNull BigDecimal requiredMonthlyContribution,
        @NonNull BigDecimal safeMonthlyContributionCapacity,
        @NonNull BigDecimal recommendedMonthlyContribution,
        @NonNull GoalFeasibility feasibility,
        @Nullable LocalDate projectedCompletionDate,
        @NonNull LocalDate evaluationDate
) {
    public GoalStrategy {
        Objects.requireNonNull(targetAmount, "targetAmount cannot be null");
        Objects.requireNonNull(currentAccumulatedAmount, "currentAccumulatedAmount cannot be null");
        Objects.requireNonNull(remainingDeficit, "remainingDeficit cannot be null");
        Objects.requireNonNull(requiredMonthlyContribution, "requiredMonthlyContribution cannot be null");
        Objects.requireNonNull(safeMonthlyContributionCapacity, "safeMonthlyContributionCapacity cannot be null");
        Objects.requireNonNull(recommendedMonthlyContribution, "recommendedMonthlyContribution cannot be null");
        Objects.requireNonNull(feasibility, "feasibility cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");
    }
}
