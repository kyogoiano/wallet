package br.com.wallet.goals.api.dto;

import br.com.wallet.goals.api.model.GoalPriority;
import br.com.wallet.goals.api.model.GoalStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
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
) {}
