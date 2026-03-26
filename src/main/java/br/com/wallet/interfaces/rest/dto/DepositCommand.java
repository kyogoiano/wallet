package br.com.wallet.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositCommand(
        @NotNull UUID walletId,
        @NotNull @Positive BigDecimal amount
) {}
