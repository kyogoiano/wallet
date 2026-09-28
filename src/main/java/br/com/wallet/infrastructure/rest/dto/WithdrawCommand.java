package br.com.wallet.infrastructure.rest.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawCommand(
        @NotNull UUID walletId,
        @Nullable UUID userId,
        @NotNull @Positive BigDecimal amount,
        @Nullable String tenantId
) {
    public WithdrawCommand(@NotNull UUID walletId, @Nullable UUID userId, @NotNull @Positive BigDecimal amount) {
        this(walletId, userId, amount, null);
    }
}
