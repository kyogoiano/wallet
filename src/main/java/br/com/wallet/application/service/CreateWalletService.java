package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.domain.event.DepositCompletedEvent;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.persistence.WalletOperationsDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class CreateWalletService implements CreateWalletUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateWalletService.class);
    private final AccountDao accountDao;
    private final WalletOperationsDao walletOperationsDao;
    private final OutboxDao outboxDao;
    private final WalletOperationService core;
    private final Clock clock;

    public CreateWalletService(final AccountDao accountDao, WalletOperationsDao walletOperationsDao, OutboxDao outboxDao, WalletOperationService core, Clock clock) {
        this.accountDao = accountDao;
        this.walletOperationsDao = walletOperationsDao;
        this.outboxDao = outboxDao;
        this.core = core;
        this.clock = clock;
    }


    @Traceable("wallet.create")
    @Override
    public void handle(UUID walletId) {
        accountDao.insertAccount(walletId);
    }


    /**
     * This only works with positive amounts
     * @param wallet object to be created, where initial balance > 0, and operationId is not null
     */
    @Traceable("wallet.createWithInitialBalance")
    @Transactional
    @Override
    public void handle(@NonNull final Wallet wallet) {
        walletOperationsDao.startOperation(wallet.operationId());
        execute(wallet);
        walletOperationsDao.completeOperation(wallet.operationId());
        log.info("Wallet created with id {}", wallet.id());
    }

    /**
     * Creates a wallet with an initial balance, so it also deposit this amount to the wallet
     * @param wallet wallet object to be created, where initial balance > 0
     */
    private void execute(@NonNull final Wallet wallet) {
        accountDao.insertAccount(wallet.id());
        log.info("Creating wallet with id {}, now we will apply a new transaction to include the initial balance {}", wallet.id(), wallet.initialBalance());
        core.applyTransaction(
                wallet.id(),
                wallet.initialBalance(),
                LedgerType.CREDIT,
                wallet.operationId(),
                clock.instant()
        );
        outboxDao.save(
                new DepositCompletedEvent(wallet.id(), wallet.initialBalance(), wallet.operationId())
        );
    }
}
