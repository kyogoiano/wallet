package br.com.wallet.infrastructure.rest.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(
        @NotNull UUID from,
        @NotNull UUID to,
        @Positive @NotNull BigDecimal amount,
        @Nullable String tenantId
) {
    public TransferCommand(@NotNull UUID from, @NotNull UUID to, @Positive @NotNull BigDecimal amount) {
        this(from, to, amount, null);
    }
}
