package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.exceptions.BusinessException;
import br.com.wallet.infrasctructure.operation.OperationStatus;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.persistence.WalletOperationsDao;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.application.utils.Validations;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.event.TransferCompletedEvent;
import br.com.wallet.exceptions.InsufficientFundsException;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Stream;

@Service
public class TransferFundsService implements TransferFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferFundsService.class);
    private final AccountDao accountDao;
    private final WalletOperationService core;
    private final OutboxDao outboxDao;
    private final WalletOperationsDao operationsDao;


    public TransferFundsService(final WalletOperationService core,
                                final OutboxDao outboxDao,
                                final WalletOperationsDao operationsDao,
                                final AccountDao accountDao) {
        this.core = core;
        this.outboxDao = outboxDao;
        this.operationsDao = operationsDao;
        this.accountDao = accountDao;
    }

    @Traceable("wallet.transfer")
    @Transactional
    @Override
    public void handle(@NonNull final Transfer transfer) throws BusinessException {

        final boolean started = operationsDao.startOperation(transfer.operationId());

        if (!started) {
            final var status = operationsDao.getStatus(transfer.operationId());

            if (status == OperationStatus.COMPLETED) {
                log.info("Idempotent skip {}", transfer.operationId());
                return;
            }

            log.warn("Recovering operation {}", transfer.operationId());
        }

        this.execute(transfer);
        outboxDao.save(
                new TransferCompletedEvent(transfer.from(), transfer.to(), transfer.amount(), transfer.operationId())
        );

        operationsDao.completeOperation(transfer.operationId());
    }

    protected void execute(@NonNull final Transfer transfer) {

        // validations
        Validations.validatePositiveAmount(transfer.amount());

        if (transfer.from().equals(transfer.to())) {
            log.warn("Invalid transfer: same wallet. walletId={}", transfer.from());
            throw new IllegalArgumentException("Cannot transfer to same wallet");
        }

        // 🔒 lock ordering (avoid deadlocks)
        final var ordered = Stream.of(transfer.from(), transfer.to())
                .sorted()
                .toList();

        final var balances = accountDao.getBalancesFromWallets(ordered);

        if (!balances.containsKey(transfer.from()) || !balances.containsKey(transfer.to())) {
            log.warn("At least one Wallet not found!");
            throw new IllegalArgumentException("At least one Wallet not found");
        }

        final var fromBalance = balances.get(transfer.from());

        if (fromBalance.compareTo(transfer.amount()) < 0) {
            log.warn("Insufficient funds. walletId={}, balance={}, amount={}",
                    transfer.from(), fromBalance, transfer.amount());
            throw new InsufficientFundsException();
        }

        final var now = Instant.now();
        core.applyTransaction(transfer.from(), transfer.amount(), LedgerType.DEBIT, transfer.operationId(), now);
        core.applyTransaction(transfer.to(), transfer.amount(), LedgerType.CREDIT, transfer.operationId(), now);

        // 🧾 ledger entries
        log.info("Transfer completed. from={}, to={}, amount={}, operationId={}",
                transfer.from(), transfer.to(), transfer.amount(), transfer.operationId());
    }

}
