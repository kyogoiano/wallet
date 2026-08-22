package br.com.wallet.infrastructure.rest.mapper;

import br.com.wallet.wallet.api.domain.LedgerEntry;
import br.com.wallet.infrastructure.rest.dto.LedgerEntryResponse;

public class LedgerMapper {
    public static LedgerEntryResponse toResponse(final LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.walletId(),
                entry.amount(),
                entry.type().name(),
                entry.operationId(),
                entry.userId(),
                entry.sequence(),
                entry.hash(),
                entry.previousHash(),
                entry.createdAt()
        );
    }
}
