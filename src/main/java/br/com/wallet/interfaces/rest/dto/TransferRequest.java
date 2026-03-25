package br.com.wallet.interfaces.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        UUID from,
        UUID to,
        BigDecimal amount,
        UUID operationId
) {}
