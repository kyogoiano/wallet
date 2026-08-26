package br.com.wallet.ledger.internal.service;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.exceptions.AccountNotFoundException;
import br.com.wallet.ledger.api.exceptions.UserNotAllowedException;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.internal.utils.Validations;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class WithdrawFundsService implements WithdrawFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawFundsService.class);

    private final WalletOperationService core;
    private final WalletOperationsDao operationsDao;
    private final OutboxDao<WithdrawCompletedEvent> outboxDao;
    private final AccountDao accountDao;
    private final Clock clock;


    public WithdrawFundsService(final WalletOperationService core,
                                final WalletOperationsDao operationsDao,
                                final OutboxDao<WithdrawCompletedEvent> outboxDao,
                                final AccountDao accountDao,
                                final Clock clock) {
        this.core = core;
        this.operationsDao = operationsDao;
        this.outboxDao = outboxDao;
        this.accountDao = accountDao;
        this.clock = clock;
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

    protected void execute(@NonNull final Withdraw withdraw) {

        final var userBalance = accountDao.findWalletBalanceForUpdate(withdraw.walletId())
                .orElseThrow(AccountNotFoundException::new);

        final UUID effectiveUserId = withdraw.userId() != null ? withdraw.userId() : userBalance.userId();
        if (!effectiveUserId.equals(userBalance.userId())) {
            throw new UserNotAllowedException(withdraw.userId(), userBalance.userId());
        }

        if (userBalance.balance().compareTo(withdraw.amount()) < 0) {
            log.warn("Insufficient funds. walletId={}, balance={}, amount={}",
                    withdraw.walletId(), userBalance, withdraw.amount());
            throw new InsufficientFundsException();
        }

        final var now = clock.instant();

        core.applyTransaction(withdraw.walletId(), withdraw.amount(), LedgerType.DEBIT, withdraw.operationId(), effectiveUserId, now);

    }
}
