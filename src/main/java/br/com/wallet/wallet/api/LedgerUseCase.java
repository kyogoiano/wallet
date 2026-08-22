package br.com.wallet.wallet.api;

import br.com.wallet.wallet.api.domain.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface LedgerUseCase {
    List<LedgerEntry> getLedger(UUID walletId, Integer limit);
}
