package br.com.wallet.interfaces.rest.mapper;

import br.com.wallet.domain.LedgerEntry;
import br.com.wallet.interfaces.rest.dto.LedgerEntryResponse;

public class LedgerMapper {
    public static LedgerEntryResponse toResponse(final LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.walletId(),
                entry.amount(),
                entry.type().name(),
                entry.operationId(),
                entry.sequence(),
                entry.hash(),
                entry.previousHash(),
                entry.createdAt()
        );
    }
}
