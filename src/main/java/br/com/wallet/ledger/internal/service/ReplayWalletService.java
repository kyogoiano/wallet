package br.com.wallet.ledger.internal.service;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.ledger.api.ReplayWalletUseCase;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.internal.persistence.LedgerDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ReplayWalletService implements ReplayWalletUseCase {

    private final LedgerDao ledgerDao;

    public ReplayWalletService(final LedgerDao ledgerDao) {
        this.ledgerDao = ledgerDao;
    }

    @Traceable("wallet.replayWallet")
    @Override
    public BigDecimal execute(@NonNull UUID walletId) {
        final var entries = ledgerDao.getLedgerEntries(walletId);

        var balance = BigDecimal.ZERO;

        for (final var entry : entries) {
            if (entry.type() == LedgerType.CREDIT) {
                balance = balance.add(entry.amount());
            } else {
                balance = balance.subtract(entry.amount());
            }
        }

        return balance;
    }
}
