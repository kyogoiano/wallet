package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
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
    public void execute(@NonNull final UUID from,
                        @NonNull final UUID to,
                        @NonNull final BigDecimal amount,
                        @NonNull final UUID operationId) {

        // validations
        Validations.validatePositiveAmount(amount);

        if (from.equals(to)) {
            log.warn("Invalid transfer: same wallet. walletId={}", from);
            throw new IllegalArgumentException("Cannot transfer to same wallet");
        }

        if (operationsDao.registerOperation(operationId)) {
            log.info("Idempotent operation ignored. operationId={}", operationId);
            return; // idempotent: already processed!
        }

        // 🔒 lock ordering (avoid deadlocks)
        final var ordered = Stream.of(from, to)
                .sorted()
                .toList();

        final var balances = accountDao.getBalancesFromWallets(ordered);

        if (!balances.containsKey(from) || !balances.containsKey(to)) {
            log.warn("At least one Wallet not found!");
            throw new IllegalArgumentException("At least one Wallet not found");
        }

        final var fromBalance = balances.get(from);

        if (fromBalance.compareTo(amount) < 0) {
            log.warn("Insufficient funds. walletId={}, balance={}, amount={}",
                    from, fromBalance, amount);
            throw new InsufficientFundsException();
        }

        final var now = Instant.now();
        core.applyTransaction(from, amount.negate(), LedgerType.DEBIT, operationId, now);
        core.applyTransaction(to, amount, LedgerType.CREDIT, operationId, now);

        // 🧾 ledger entries
        log.info("Transfer completed. from={}, to={}, amount={}, operationId={}",
                from, to, amount, operationId);
        outboxDao.save(
                new TransferCompletedEvent(from, to, amount, operationId)
        );
    }

}
