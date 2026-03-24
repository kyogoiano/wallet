package br.com.wallet.application.service;

import br.com.wallet.application.usecase.BalanceUseCase;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import br.com.wallet.infrasctructure.persistence.LedgerDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * This Service uses a CQRS styled design
 */

@Service
public class BalanceService implements BalanceUseCase {


    private final AccountDao accountDao;
    private final LedgerDao ledgerDao;

    public BalanceService(final AccountDao accountDao, LedgerDao ledgerDao) {
        this.accountDao = accountDao;
        this.ledgerDao = ledgerDao;
    }

    @Override
    public BigDecimal getBalance(@NonNull UUID walletId) {
        return accountDao.findWalletBalance(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    @Override
    public BigDecimal getHistoricalBalance(@NonNull UUID walletId, @NonNull Instant createdAt) {
        return ledgerDao.getBalanceAt(walletId, createdAt);
    }
}
