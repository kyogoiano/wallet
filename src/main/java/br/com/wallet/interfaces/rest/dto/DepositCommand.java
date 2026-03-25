package br.com.wallet.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositCommand(
        @NotNull UUID walletId,
        @NotNull BigDecimal amount
) {}
