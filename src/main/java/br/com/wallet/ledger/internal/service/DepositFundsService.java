package br.com.wallet.ledger.internal.service;

import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.exceptions.AccountNotFoundException;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.internal.utils.Validations;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.api.event.DepositCompletedEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class DepositFundsService implements DepositFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DepositFundsService.class);

    private final WalletOperationService core;
    private final WalletOperationsDao operationsDao;
    private final OutboxDao outboxDao;
    private final AccountDao accountDao;
    private final Clock clock;

    public DepositFundsService(final WalletOperationService core,
                               final WalletOperationsDao operationsDao,
                               final OutboxDao outboxDao,
                               final AccountDao accountDao,
                               final FraudCheckHelper fraudCheckHelper, Clock clock) { // Adjust constructor
        this.core = core;
        this.operationsDao = operationsDao;
        this.outboxDao = outboxDao;
        this.accountDao = accountDao;
        this.clock = clock;
    }

    @Traceable("wallet.deposit")
    @Transactional
    @Override
    public void handle(@NonNull final Deposit deposit) {

        if (!operationsDao.startOperation(deposit.operationId())) {
            log.info("Idempotent operation ignored. operationId={}", deposit.operationId());
            throw new IdempotencyException("Operation already processed: " + deposit.operationId());
        }

        // this operation might fail if someone deletes the user account in the meantime, as we not lock it for update
        final var userId = deposit.userId() != null ? deposit.userId() : accountDao.findUserId(deposit.walletId()).orElseThrow(AccountNotFoundException::new);

        // validations
        Validations.validatePositiveAmount(deposit.amount());

        this.execute(deposit, userId);

        outboxDao.save(
                new DepositCompletedEvent(deposit.walletId(), deposit.amount(), deposit.operationId())
        );
        operationsDao.completeOperation(deposit.operationId());
    }

    protected void execute(@NonNull final Deposit deposit, @NonNull final UUID userId) {

        final var now = clock.instant(); // Use Clock directly or inject if needed for tests
        core.applyTransaction(deposit.walletId(), deposit.amount(), LedgerType.CREDIT, deposit.operationId(), userId, now);
    }
}
