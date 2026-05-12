package br.com.wallet.interfaces.rest.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositCommand(
        @NotNull UUID walletId,
        @Nullable UUID userId,
        @NotNull @Positive BigDecimal amount
) {}
