package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class CreateWalletService implements CreateWalletUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateWalletService.class);
    private final AccountDao accountDao;
    private final WalletOperationService core;
    private final Clock clock;

    public CreateWalletService(final AccountDao accountDao, WalletOperationService core, Clock clock) {
        this.accountDao = accountDao;
        this.core = core;
        this.clock = clock;
    }


    @Traceable("wallet.create")
    @Override
    public UUID execute() {
        final UUID walletId = UUID.randomUUID();

        accountDao.insertAccount(walletId);

        return walletId;
    }


    /**
     * This only works with positive amounts
     * @param wallet where initial balance > 0
     * @return return wallet id
     */
    @Traceable("wallet.createWithInitialBalance")
    @Override
    public UUID execute(@NonNull final Wallet wallet) {
        final UUID walletId = UUID.randomUUID();

        accountDao.insertAccount(walletId);
        log.info("Creating wallet with id {}, now we will apply a new transaction to include the initial balance {}", walletId, wallet.initialBalance());
        core.applyTransaction(
                walletId,
                wallet.initialBalance(),
                LedgerType.CREDIT,
                wallet.operationId(),
                clock.instant()
        );

        return walletId;
    }
}
