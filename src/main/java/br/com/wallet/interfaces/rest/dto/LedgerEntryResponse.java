package br.com.wallet.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID walletId,
        BigDecimal amount,
        String type,
        UUID operationId,
        UUID userId,
        Long sequence,
        String hash,
        String previousHash,
        Instant createdAt
) {}
