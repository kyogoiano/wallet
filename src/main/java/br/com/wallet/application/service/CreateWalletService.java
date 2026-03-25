package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class CreateWalletService implements CreateWalletUseCase {

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
     * @param initialBalance initial balance > 0
     * @return return wallet id
     */
    @Override
    public UUID execute(@Nonnull final BigDecimal initialBalance) {
        final UUID walletId = UUID.randomUUID();

        accountDao.insertAccount(walletId);

        core.applyTransaction(
                walletId,
                initialBalance,
                LedgerType.CREDIT,
                UUID.randomUUID(),
                clock.instant()
        );

        return walletId;
    }
}
