package br.com.wallet.wallet.internal.service;

import br.com.wallet.wallet.api.domain.LedgerType;
import br.com.wallet.wallet.internal.persistence.AccountDao;
import br.com.wallet.wallet.internal.persistence.LedgerDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletOperationService {
    private static final Logger log = LoggerFactory.getLogger(WalletOperationService.class);


    private final LedgerDao ledgerDao;
    private final AccountDao accountDao;

    public WalletOperationService(final LedgerDao ledgerDao, final AccountDao accountDao) {
        this.ledgerDao = ledgerDao;
        this.accountDao = accountDao;
    }

    /**
     * Core transaction operations (technical execution)
     * * Stateless and reusable *
     * * sequence is updated together with balance, what prevents race conditions conflicts
     * * Ghost reads problem solution is present on the ledger insert operation
     *
     * @param walletId    wallet id (account)
     * @param amount      operation on amount of ( IMPORTANT NOTICE: do not use negative values for DEBIT ops)
     * @param ledgerType  type of operation
     * @param operationId operation id
     * @param userId      user id
     * @param now         operation instant
     */
    public void applyTransaction(
            @NonNull UUID walletId,
            @NonNull BigDecimal amount,
            @NonNull LedgerType ledgerType,
            @NonNull UUID operationId,
            @NonNull UUID userId,
            @NonNull Instant now) {

        final BigDecimal signedAmount = (ledgerType == LedgerType.DEBIT)
                ? amount.negate()
                : amount;
        // balance update and return current sequence
        final var sequence = accountDao.updateBalance(walletId, signedAmount);
        log.info("Applying transaction sequence {} to wallet {}, with amount {}", sequence, walletId, amount);

        // ledger insert with hash calculation should be matched with previous sequence ( so even on race conditions it will follow the right sequence )
        ledgerDao.insertLedger(walletId, amount, ledgerType, operationId, userId, sequence, now);
        log.info("Transaction applied!, ledger entry created with wallet id: {}, sequence: {}, operationId: {}, userId: {}",
                walletId, sequence, operationId, userId);
    }

}
