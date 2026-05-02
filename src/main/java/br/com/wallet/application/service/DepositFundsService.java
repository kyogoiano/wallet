package br.com.wallet.application.service;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.domain.context.Deposit;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.exceptions.AccountNotFoundException;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.persistence.WalletOperationsDao;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.DepositFundsUseCase;
import br.com.wallet.application.utils.Validations;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.event.DepositCompletedEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
class DepositFundsService implements DepositFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DepositFundsService.class);

    private final WalletOperationService core;
    private final WalletOperationsDao operationsDao;
    private final OutboxDao outboxDao;
    private final AccountDao accountDao;

    public DepositFundsService(final WalletOperationService core,
                               final WalletOperationsDao operationsDao, OutboxDao outboxDao, AccountDao accountDao) {
        this.core = core;
        this.operationsDao = operationsDao;
        this.outboxDao = outboxDao;
        this.accountDao = accountDao;
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
        var userId = accountDao.findUserId(deposit.walletId()).orElseThrow(AccountNotFoundException::new);

        // validations
        Validations.validatePositiveAmount(deposit.amount());

        this.execute(deposit, userId);
    }

    protected void execute(@NonNull Deposit deposit, @NonNull UUID userId) {

        final var now = Instant.now();
        core.applyTransaction(deposit.walletId(), deposit.amount(), LedgerType.CREDIT, deposit.operationId(), userId, now);
        outboxDao.save(
                new DepositCompletedEvent(deposit.walletId(), deposit.amount(), deposit.operationId())
        );
        operationsDao.completeOperation(deposit.operationId());
    }


}
