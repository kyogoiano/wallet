package br.com.wallet.interfaces.rest.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateWalletCommand(

        @PositiveOrZero
        BigDecimal initialBalance

) {}
