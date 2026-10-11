package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.domain.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface LedgerUseCase {
    List<LedgerEntry> getLedger(UUID walletId, Integer limit);
}
