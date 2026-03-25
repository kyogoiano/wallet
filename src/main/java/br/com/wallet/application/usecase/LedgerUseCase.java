package br.com.wallet.application.usecase;

import br.com.wallet.domain.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface LedgerUseCase {
    List<LedgerEntry> getLedger(UUID walletId, Integer limit);
}
