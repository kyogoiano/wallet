package br.com.wallet.wallet.internal.service;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.wallet.api.LedgerUseCase;
import br.com.wallet.wallet.api.domain.LedgerEntry;
import br.com.wallet.wallet.internal.persistence.LedgerDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerService implements LedgerUseCase {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerDao ledgerDao;

    public LedgerService(final LedgerDao ledgerDao) {
        this.ledgerDao = ledgerDao;
    }

    @Traceable("wallet.getLedger")
    @Override
    public List<LedgerEntry> getLedger(@NonNull UUID walletId, Integer limit) {
        log.info("Getting ledger for walletId={}", walletId);
        return ledgerDao.getLedgerEntries(walletId, limit);
    }
}
