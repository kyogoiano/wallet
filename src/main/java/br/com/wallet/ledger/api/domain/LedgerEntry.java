package br.com.wallet.ledger.api.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
        UUID walletId,
        BigDecimal amount,
        LedgerType type,
        UUID operationId,
        UUID userId,
        Long sequence,
        String hash,
        String previousHash,
        Instant createdAt,
        String tenantId
) {
    public LedgerEntry {
        if (tenantId == null) {
            tenantId = "default";
        }
    }

    public LedgerEntry(
            UUID walletId,
            BigDecimal amount,
            LedgerType type,
            UUID operationId,
            UUID userId,
            Long sequence,
            String hash,
            String previousHash,
            Instant createdAt
    ) {
        this(walletId, amount, type, operationId, userId, sequence, hash, previousHash, createdAt, "default");
    }
}
