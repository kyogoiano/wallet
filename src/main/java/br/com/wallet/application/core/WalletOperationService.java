package br.com.wallet.application.core;

import br.com.wallet.domain.LedgerType;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import br.com.wallet.infrasctructure.persistence.LedgerDao;
import br.com.wallet.util.HashUtils;
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
     * @param walletId wallet id (account)
     * @param amount operation on amount of ( IMPORTANT NOTICE: do not use negative values for DEBIT ops)
     * @param ledgerType type of operation
     * @param operationId operation id
     * @param now operation instant
     */
    public void applyTransaction(
            @NonNull UUID walletId,
            @NonNull BigDecimal amount,
            @NonNull LedgerType ledgerType,
            @NonNull UUID operationId,
            @NonNull Instant now) {

        // sequence
        final Long sequence = accountDao.nextAccountSequence(walletId);


        final BigDecimal signedAmount = (ledgerType == LedgerType.DEBIT)
                ? amount.negate()
                : amount;
        // balance update
        accountDao.updateBalance(walletId, signedAmount);
        log.info("Applying transaction sequence {} to wallet {}, with amount {}", sequence, walletId, amount);
        // hash
        final var previousHash = ledgerDao.findPreviousHash(walletId);
        final var hash = HashUtils.calculateHash(previousHash, walletId, amount, ledgerType, sequence, operationId, now);

        // ledger insert
        ledgerDao.insertLedger(walletId, amount, ledgerType, operationId, sequence, hash, now, previousHash);
        log.info("Transaction applied!, ledger entry created with wallet id: {}, sequence: {}, operationId: {}", walletId, sequence, operationId);
    }

}
