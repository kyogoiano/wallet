package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.persistence.WalletOperationsDao;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.WithdrawFundsUseCase;
import br.com.wallet.application.utils.Validations;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.event.WithdrawCompletedEvent;
import br.com.wallet.exceptions.InsufficientFundsException;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class WithdrawFundsService implements WithdrawFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawFundsService.class);

    private final WalletOperationService core;
    private final WalletOperationsDao operationsDao;
    private final OutboxDao outboxDao;
    private final AccountDao accountDao;


    public WithdrawFundsService(final WalletOperationService core,
                                final WalletOperationsDao operationsDao,
                                final OutboxDao outboxDao,
                                final AccountDao accountDao) {
        this.core = core;
        this.operationsDao = operationsDao;
        this.outboxDao = outboxDao;
        this.accountDao = accountDao;
    }

    @Traceable("wallet.withdraw")
    @Transactional
    @Override
    public void execute(@NonNull UUID walletId, @NonNull BigDecimal amount, @NonNull UUID operationId) {
        // validations
        Validations.validatePositiveAmount(amount);

        if (operationsDao.registerOperation(operationId)) {
            log.info("Idempotent operation ignored. operationId={}", operationId);
            return; // idempotent: already processed!
        }
        final var balance = accountDao.findWalletBalanceForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
        if (balance.compareTo(amount) < 0) {
            log.warn("Insufficient funds. walletId={}, balance={}, amount={}",
                    walletId, balance, amount);
            throw new InsufficientFundsException();
        }

        final var now = Instant.now();
        core.applyTransaction(walletId, amount.negate(), LedgerType.DEBIT, operationId, now);
        outboxDao.save(
                new WithdrawCompletedEvent(walletId, amount, operationId)
        );
    }
}
