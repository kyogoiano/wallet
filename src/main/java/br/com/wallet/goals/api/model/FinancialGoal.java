package br.com.wallet.goals.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record FinancialGoal(
        @NonNull UUID id,
        @NonNull UUID userId,
        @NonNull UUID walletId,
        @Nullable UUID targetWalletId,
        @NonNull String name,
        @NonNull BigDecimal targetAmount,
        @NonNull LocalDate targetDate,
        @NonNull GoalPriority priority,
        @NonNull GoalStatus status,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {
    public FinancialGoal {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(targetAmount, "targetAmount cannot be null");
        Objects.requireNonNull(targetDate, "targetDate cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

        if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetAmount must be strictly positive");
        }
    }

    public boolean isActive() {
        return status == GoalStatus.ACTIVE;
    }
}
