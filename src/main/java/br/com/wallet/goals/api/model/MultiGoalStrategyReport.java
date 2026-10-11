package br.com.wallet.goals.api.model;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MultiGoalStrategyReport(
        @NonNull UUID walletId,
        @NonNull BigDecimal totalMonthlyIncome,
        @NonNull BigDecimal totalCommittedExpenses,
        @NonNull BigDecimal minimumSafetyBuffer,
        @NonNull BigDecimal totalSafeCapacity,
        @NonNull BigDecimal totalRequiredContributions,
        @NonNull BigDecimal totalRecommendedContributions,
        @NonNull List<GoalStrategy> goalStrategies,
        @NonNull LocalDate evaluationDate
) {
    public MultiGoalStrategyReport {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(totalMonthlyIncome, "totalMonthlyIncome cannot be null");
        Objects.requireNonNull(totalCommittedExpenses, "totalCommittedExpenses cannot be null");
        Objects.requireNonNull(minimumSafetyBuffer, "minimumSafetyBuffer cannot be null");
        Objects.requireNonNull(totalSafeCapacity, "totalSafeCapacity cannot be null");
        Objects.requireNonNull(totalRequiredContributions, "totalRequiredContributions cannot be null");
        Objects.requireNonNull(totalRecommendedContributions, "totalRecommendedContributions cannot be null");
        Objects.requireNonNull(goalStrategies, "goalStrategies cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");
        goalStrategies = List.copyOf(goalStrategies);
    }
}
