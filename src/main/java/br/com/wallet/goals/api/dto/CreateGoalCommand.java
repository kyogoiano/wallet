package br.com.wallet.goals.api.dto;

import br.com.wallet.goals.api.model.GoalPriority;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGoalCommand(
        @NotNull @NonNull UUID userId,
        @NotNull @NonNull UUID walletId,
        @Nullable UUID targetWalletId,
        @NotBlank @NonNull String name,
        @NotNull @DecimalMin("0.01") @NonNull BigDecimal targetAmount,
        @NotNull @Future @NonNull LocalDate targetDate,
        @NotNull @NonNull GoalPriority priority
) {}
