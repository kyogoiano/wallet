package br.com.wallet.interfaces.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawRequest(
        UUID walletId,
        BigDecimal amount,
        UUID operationId
) {}
