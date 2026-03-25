package br.com.wallet.interfaces.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositRequest(
        UUID walletId,
        BigDecimal amount,
        UUID operationId
) {}
