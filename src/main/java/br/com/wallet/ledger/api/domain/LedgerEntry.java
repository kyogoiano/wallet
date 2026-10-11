package br.com.wallet.ledger.api.domain;
 
import br.com.wallet.core.exceptions.TenantContextMissingException;

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
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for LedgerEntry");
        }
    }
}
