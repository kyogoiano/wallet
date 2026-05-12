package br.com.wallet.application.service;

import br.com.wallet.application.fraud.FraudCheckHelper;
import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.domain.event.DepositCompletedEvent;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.persistence.WalletOperationsDao;
import jakarta.validation.constraints.NotNull;
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
    private final FraudCheckHelper fraudCheckHelper;

    public CreateWalletService(final AccountDao accountDao,
                               final WalletOperationsDao walletOperationsDao,
                               final OutboxDao outboxDao,
                               final WalletOperationService core,
                               final Clock clock, FraudCheckHelper fraudCheckHelper) {
        this.accountDao = accountDao;
        this.walletOperationsDao = walletOperationsDao;
        this.outboxDao = outboxDao;
        this.core = core;
        this.clock = clock;
        this.fraudCheckHelper = fraudCheckHelper;
    }


    @Traceable("wallet.create")
    @Override
    public void handle(@NonNull UUID walletId, @NotNull UUID userId) {
        accountDao.insertAccount(walletId, userId);
    }


    /**
     * This only works with positive amounts
     * @param wallet object to be created, where initial balance > 0, and operationId is not null
     */
    @Traceable("wallet.createWithInitialBalance")
    @Transactional
    @Override
    public void handle(@NonNull final Wallet wallet) {
        fraudCheckHelper.performFraudCheck(wallet);
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
        accountDao.insertAccount(wallet.id(), wallet.userId());
        log.info("Creating wallet with id {}, now we will apply a new transaction to include the initial balance {}", wallet.id(), wallet.initialBalance());
        core.applyTransaction(
                wallet.id(),
                wallet.initialBalance(),
                LedgerType.CREDIT,
                wallet.operationId(),
                wallet.userId(),
                clock.instant()
        );
        outboxDao.save(
                new DepositCompletedEvent(wallet.id(), wallet.initialBalance(), wallet.operationId())
        );
    }
}
