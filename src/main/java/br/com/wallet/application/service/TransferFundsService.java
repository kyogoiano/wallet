package br.com.wallet.application.service;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.core.exceptions.IdempotencyException;
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

import java.time.Clock;
import java.util.stream.Stream;

@Service
public class TransferFundsService implements TransferFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferFundsService.class);
    private final AccountDao accountDao;
    private final WalletOperationService core;
    private final OutboxDao outboxDao;
    private final WalletOperationsDao operationsDao;
    private final Clock clock;

    public TransferFundsService(final WalletOperationService core,
                                final OutboxDao outboxDao,
                                final WalletOperationsDao operationsDao,
                                final AccountDao accountDao,
                                final Clock clock) {
        this.core = core;
        this.outboxDao = outboxDao;
        this.operationsDao = operationsDao;
        this.accountDao = accountDao;
        this.clock = clock;
    }


    /**
     * Transfer funds using deterministic lock ordering
     * Desired flow:
     * -------------------------------------------
     * Step	Pod 1	                Pod 2
     * 1	locks A	                waits on A
     * 2	locks B	                still waiting
     * 3	executes transfer A→B	still waiting
     * 4	commits (releases A, B)	locks A
     * 5	—	                    locks B
     * 6	—	                    executes transfer B→A
     * -------------------------------------------
     * Deadlock flow:
     * -------------------------------------------
     * Step	Pod 1	                Pod 2
     * 1	locks A	                locks B
     * 2	waits on B	            waits on A
     * -------------------------------------------
     * Now both are waiting forever → 💥 deadlock
     * ------------------------------------------
     * * The database detects this and kills one transaction!
     *
     * @param transfer transfer object
     */
    @Traceable("wallet.transfer")
    @Transactional
    @Override
    public void handle(@NonNull final Transfer transfer) {

        // validations
        Validations.validatePositiveAmount(transfer.amount());

        final boolean started = operationsDao.startOperation(transfer.operationId());

        if (!started) {
            final var status = operationsDao.getStatus(transfer.operationId());

            if (status == OperationStatus.COMPLETED) {
                log.info("Idempotent skip {}", transfer.operationId());
                throw new IdempotencyException("Operation already processed: " + transfer.operationId());
            }

            log.warn("Recovering operation {}", transfer.operationId());
        }


        if (transfer.from().equals(transfer.to())) {
            log.warn("Invalid transfer: same wallet. walletId={}", transfer.from());
            throw new IllegalArgumentException("Cannot transfer to same wallet");
        }

        this.execute(transfer);
        outboxDao.save(
                new TransferCompletedEvent(transfer.from(), transfer.to(), transfer.amount(), transfer.operationId())
        );

        operationsDao.completeOperation(transfer.operationId());
    }

    protected void execute(@NonNull final Transfer transfer) {


        // 🔒 lock ordering (avoid deadlocks: no circular wait)
        final var ordered = Stream.of(transfer.from(), transfer.to())
                .sorted()
                .toList();

        final var accountBalances = accountDao.getBalancesFromWallets(ordered);

        if (!accountBalances.containsKey(transfer.from()) || !accountBalances.containsKey(transfer.to())) {
            log.warn("At least one Wallet not found!");
            throw new IllegalArgumentException("At least one Wallet not found");
        }

        final var fromBalance = accountBalances.get(transfer.from());

        if (fromBalance.balance().compareTo(transfer.amount()) < 0) {
            log.warn("Insufficient funds. walletId={}, balance={}, amount={}",
                    transfer.from(), fromBalance, transfer.amount());
            throw new InsufficientFundsException();
        }

        // check for frauds
        final var toBalance = accountBalances.get(transfer.to());

        final var now = clock.instant();

        // each child transaction unlock one wallet , first from wallet and then to wallet
        core.applyTransaction(transfer.from(), transfer.amount(), LedgerType.DEBIT, transfer.operationId(), fromBalance.userId(), now);

        core.applyTransaction(transfer.to(), transfer.amount(), LedgerType.CREDIT, transfer.operationId(), toBalance.userId(), now);

        // 🧾 ledger entries
        log.info("Transfer completed. from={}, to={}, amount={}, operationId={}",
                transfer.from(), transfer.to(), transfer.amount(), transfer.operationId());
    }
}
