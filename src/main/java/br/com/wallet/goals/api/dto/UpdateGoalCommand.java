package br.com.wallet.goals.api.dto;

import br.com.wallet.goals.api.model.GoalPriority;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGoalCommand(
        @NotBlank @NonNull String name,
        @NotNull @DecimalMin("0.01") @NonNull BigDecimal targetAmount,
        @NotNull @Future @NonNull LocalDate targetDate,
        @NotNull @NonNull GoalPriority priority
) {}
