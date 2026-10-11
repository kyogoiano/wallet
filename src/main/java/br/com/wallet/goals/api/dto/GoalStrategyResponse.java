package br.com.wallet.goals.api.dto;

import br.com.wallet.goals.api.model.GoalFeasibility;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoalStrategyResponse(
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
) {}
