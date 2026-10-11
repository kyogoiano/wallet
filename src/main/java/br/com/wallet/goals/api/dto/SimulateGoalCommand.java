package br.com.wallet.goals.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SimulateGoalCommand(
        @NotNull @DecimalMin("0.01") @NonNull BigDecimal targetAmount,
        @NotNull @Future @NonNull LocalDate targetDate,
        @NotNull @DecimalMin("0.00") @NonNull BigDecimal currentBalance,
        @NotNull @DecimalMin("0.00") @NonNull BigDecimal monthlyIncome,
        @NotNull @DecimalMin("0.00") @NonNull BigDecimal monthlyCommittedExpenses,
        @NotNull @DecimalMin("0.00") @NonNull BigDecimal minimumSafetyBuffer
) {}
