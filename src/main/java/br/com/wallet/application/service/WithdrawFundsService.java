package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.domain.context.Withdraw;
import br.com.wallet.exceptions.IdempotencyException;
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

import java.time.Instant;

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
    public void handle(@NonNull final Withdraw withdraw) {

        if (!operationsDao.startOperation(withdraw.operationId())) {
            log.info("Idempotent operation ignored. operationId={}", withdraw.operationId());
            throw new IdempotencyException("Operation already processed: " + withdraw.operationId());
        }

        this.execute(withdraw);
    }

    protected void execute(@NonNull Withdraw withdraw) {
        // validations
        Validations.validatePositiveAmount(withdraw.amount());

        final var balance = accountDao.findWalletBalanceForUpdate(withdraw.walletId())
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
        if (balance.compareTo(withdraw.amount()) < 0) {
            log.warn("Insufficient funds. walletId={}, balance={}, amount={}",
                    withdraw.walletId(), balance, withdraw.amount());
            throw new InsufficientFundsException();
        }

        final var now = Instant.now();
        core.applyTransaction(withdraw.walletId(), withdraw.amount(), LedgerType.DEBIT, withdraw.operationId(), now);
        outboxDao.save(
                new WithdrawCompletedEvent(withdraw.walletId(), withdraw.amount(), withdraw.operationId())
        );
    }
}
