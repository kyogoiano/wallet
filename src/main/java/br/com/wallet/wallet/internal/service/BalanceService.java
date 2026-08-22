package br.com.wallet.wallet.internal.service;

import br.com.wallet.wallet.api.BalanceUseCase;
import br.com.wallet.wallet.internal.persistence.AccountDao;
import br.com.wallet.wallet.internal.persistence.LedgerDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * This Service uses a CQRS styled design
 */

@Service
public class BalanceService implements BalanceUseCase {


    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);
    private final AccountDao accountDao;
    private final LedgerDao ledgerDao;

    public BalanceService(final AccountDao accountDao, LedgerDao ledgerDao) {
        this.accountDao = accountDao;
        this.ledgerDao = ledgerDao;
    }

    @Override
    public BigDecimal getBalance(@NonNull UUID walletId) {
        log.info("Retrieving balance for wallet {}", walletId);
        return accountDao.findWalletBalance(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    @Override
    public BigDecimal getHistoricalBalance(@NonNull UUID walletId, @NonNull Instant createdAt) {
        log.info("Retrieving historical balance for wallet {}, at {}", walletId, createdAt);
        return ledgerDao.getBalanceAt(walletId, createdAt);
    }
}
