package br.com.wallet.application.service;

import br.com.wallet.application.fraud.FraudCheckHelper;
import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.domain.context.Withdraw;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.exceptions.AccountNotFoundException;
import br.com.wallet.exceptions.UserNotAllowedException;
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

import java.time.Clock;

@Service
public class WithdrawFundsService implements WithdrawFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawFundsService.class);

    private final WalletOperationService core;
    private final WalletOperationsDao operationsDao;
    private final OutboxDao outboxDao;
    private final AccountDao accountDao;
    private final Clock clock;
    private final FraudCheckHelper fraudCheckHelper;


    public WithdrawFundsService(final WalletOperationService core,
                                final WalletOperationsDao operationsDao,
                                final OutboxDao outboxDao,
                                final AccountDao accountDao,
                                final Clock clock,
                                final FraudCheckHelper fraudCheckHelper) {
        this.core = core;
        this.operationsDao = operationsDao;
        this.outboxDao = outboxDao;
        this.accountDao = accountDao;
        this.clock = clock;
        this.fraudCheckHelper = fraudCheckHelper;
    }

    @Traceable("wallet.withdraw")
    @Transactional
    @Override
    public void handle(@NonNull final Withdraw withdraw) {

        // validations
        Validations.validatePositiveAmount(withdraw.amount());

        if (!operationsDao.startOperation(withdraw.operationId())) {
            log.info("Idempotent operation ignored. operationId={}", withdraw.operationId());
            throw new IdempotencyException("Operation already processed: " + withdraw.operationId());
        }

        this.execute(withdraw);

        outboxDao.save(
                new WithdrawCompletedEvent(withdraw.walletId(), withdraw.amount(), withdraw.operationId())
        );

        operationsDao.completeOperation(withdraw.operationId());
    }

    protected void execute(@NonNull Withdraw withdraw) {

        final var userBalance = accountDao.findWalletBalanceForUpdate(withdraw.walletId())
                .orElseThrow(AccountNotFoundException::new);

        if (withdraw.userId() == null) {
            withdraw.setUserId(userBalance.userId());
        } else {
            if(!withdraw.userId().equals(userBalance.userId())) {
                throw new UserNotAllowedException(withdraw.userId(), userBalance.userId());
            }
        }

        // check for frauds
        fraudCheckHelper.performFraudCheck(withdraw);


        if (userBalance.balance().compareTo(withdraw.amount()) < 0) {
            log.warn("Insufficient funds. walletId={}, balance={}, amount={}",
                    withdraw.walletId(), userBalance, withdraw.amount());
            throw new InsufficientFundsException();
        }

        final var now = clock.instant();

        core.applyTransaction(withdraw.walletId(), withdraw.amount(), LedgerType.DEBIT, withdraw.operationId(), userBalance.userId(), now);

    }
}
