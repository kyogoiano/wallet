package br.com.wallet.goals.internal.engine;

import br.com.wallet.goals.api.model.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class GoalStrategyEngine {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private final ContributionCalculator contributionCalculator;
    private final CashflowCapacityCalculator capacityCalculator;

    public GoalStrategyEngine(
            @NonNull final ContributionCalculator contributionCalculator,
            @NonNull final CashflowCapacityCalculator capacityCalculator
    ) {
        this.contributionCalculator = Objects.requireNonNull(contributionCalculator, "contributionCalculator cannot be null");
        this.capacityCalculator = Objects.requireNonNull(capacityCalculator, "capacityCalculator cannot be null");
    }

    @NonNull
    public GoalStrategy calculate(
            @NonNull final FinancialGoal goal,
            @NonNull final BigDecimal currentBalance,
            @Nullable final CashflowProfile profile,
            @NonNull final LocalDate evaluationDate
    ) {
        Objects.requireNonNull(goal, "goal cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");

        final BigDecimal targetAmount = goal.targetAmount();
        final BigDecimal deficit = contributionCalculator.calculateDeficit(targetAmount, currentBalance);
        final long remainingMonths = contributionCalculator.calculateRemainingMonths(evaluationDate, goal.targetDate());
        final BigDecimal requiredMonthly = contributionCalculator.calculateRequiredMonthlyContribution(deficit, remainingMonths);
        final BigDecimal safeCapacity = capacityCalculator.calculateSafeMonthlyCapacity(profile);
        final BigDecimal recommendedMonthly = capacityCalculator.calculateRecommendedContribution(requiredMonthly, safeCapacity);

        final GoalFeasibility feasibility = determineFeasibility(deficit, requiredMonthly, safeCapacity);
        final LocalDate projectedDate = contributionCalculator.calculateProjectedCompletionDate(deficit, recommendedMonthly.compareTo(BigDecimal.ZERO) > 0 ? recommendedMonthly : safeCapacity, evaluationDate);

        return new GoalStrategy(
                goal.id(),
                targetAmount,
                currentBalance.setScale(SCALE, ROUNDING_MODE),
                deficit,
                remainingMonths,
                requiredMonthly,
                safeCapacity,
                recommendedMonthly,
                feasibility,
                projectedDate,
                evaluationDate
        );
    }

    @NonNull
    public MultiGoalStrategyReport evaluateWaterfall(
            @NonNull final UUID walletId,
            @NonNull final List<FinancialGoal> goals,
            @Nullable final CashflowProfile profile,
            @NonNull final BigDecimal currentBalance,
            @NonNull final LocalDate evaluationDate
    ) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(goals, "goals cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");

        final BigDecimal monthlyIncome = profile != null ? profile.monthlyIncome() : BigDecimal.ZERO;
        final BigDecimal committedExpenses = profile != null ? profile.monthlyCommittedExpenses() : BigDecimal.ZERO;
        final BigDecimal safetyBuffer = profile != null ? profile.minimumSafetyBuffer() : BigDecimal.ZERO;
        final BigDecimal totalSafeCapacity = capacityCalculator.calculateSafeMonthlyCapacity(profile);

        // Sort by priority rank (1 = CRITICAL first) then targetDate ascending
        final List<FinancialGoal> sortedGoals = goals.stream()
                .filter(FinancialGoal::isActive)
                .sorted(Comparator.comparingInt((FinancialGoal g) -> g.priority().getRank())
                        .thenComparing(FinancialGoal::targetDate))
                .toList();

        BigDecimal remainingCapacity = totalSafeCapacity;
        BigDecimal totalRequired = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        BigDecimal totalRecommended = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        final List<GoalStrategy> strategies = new ArrayList<>();

        for (FinancialGoal goal : sortedGoals) {
            final BigDecimal deficit = contributionCalculator.calculateDeficit(goal.targetAmount(), currentBalance);
            final long remainingMonths = contributionCalculator.calculateRemainingMonths(evaluationDate, goal.targetDate());
            final BigDecimal requiredMonthly = contributionCalculator.calculateRequiredMonthlyContribution(deficit, remainingMonths);
            totalRequired = totalRequired.add(requiredMonthly);

            final BigDecimal allocatedCapacity = requiredMonthly.min(remainingCapacity).setScale(SCALE, ROUNDING_MODE);
            remainingCapacity = remainingCapacity.subtract(allocatedCapacity).max(BigDecimal.ZERO);
            totalRecommended = totalRecommended.add(allocatedCapacity);

            final GoalFeasibility feasibility;
            if (deficit.compareTo(BigDecimal.ZERO) <= 0) {
                feasibility = GoalFeasibility.ACHIEVED;
            } else if (allocatedCapacity.compareTo(requiredMonthly) >= 0) {
                feasibility = GoalFeasibility.ON_TRACK;
            } else if (allocatedCapacity.compareTo(BigDecimal.ZERO) > 0) {
                feasibility = GoalFeasibility.AT_RISK;
            } else {
                feasibility = GoalFeasibility.UNACHIEVABLE;
            }

            final LocalDate projectedDate = contributionCalculator.calculateProjectedCompletionDate(
                    deficit, allocatedCapacity, evaluationDate
            );

            strategies.add(new GoalStrategy(
                    goal.id(),
                    goal.targetAmount(),
                    currentBalance.setScale(SCALE, ROUNDING_MODE),
                    deficit,
                    remainingMonths,
                    requiredMonthly,
                    totalSafeCapacity,
                    allocatedCapacity,
                    feasibility,
                    projectedDate,
                    evaluationDate
            ));
        }

        return new MultiGoalStrategyReport(
                walletId,
                monthlyIncome.setScale(SCALE, ROUNDING_MODE),
                committedExpenses.setScale(SCALE, ROUNDING_MODE),
                safetyBuffer.setScale(SCALE, ROUNDING_MODE),
                totalSafeCapacity,
                totalRequired,
                totalRecommended,
                strategies,
                evaluationDate
        );
    }

    private GoalFeasibility determineFeasibility(
            @NonNull final BigDecimal deficit,
            @NonNull final BigDecimal requiredMonthly,
            @NonNull final BigDecimal safeCapacity
    ) {
        if (deficit.compareTo(BigDecimal.ZERO) <= 0) {
            return GoalFeasibility.ACHIEVED;
        }
        if (safeCapacity.compareTo(requiredMonthly) >= 0) {
            return GoalFeasibility.ON_TRACK;
        }
        if (safeCapacity.compareTo(BigDecimal.ZERO) > 0) {
            return GoalFeasibility.AT_RISK;
        }
        return GoalFeasibility.UNACHIEVABLE;
    }
}
