package br.com.wallet.goals.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaveCashflowProfileCommand(
        @NotNull @NonNull UUID userId,
        @NotNull @NonNull UUID walletId,
        @NotNull @DecimalMin("0.00") @NonNull BigDecimal monthlyIncome,
        @NotNull @DecimalMin("0.00") @NonNull BigDecimal monthlyCommittedExpenses,
        @NotNull @DecimalMin("0.00") @NonNull BigDecimal minimumSafetyBuffer
) {}
