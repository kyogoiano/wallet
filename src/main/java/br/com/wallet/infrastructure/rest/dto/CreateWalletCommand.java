package br.com.wallet.infrastructure.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateWalletCommand(

        @PositiveOrZero
        BigDecimal initialBalance,
        @NotNull
        UUID userId
) {}
