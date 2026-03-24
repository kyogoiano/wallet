package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.infrasctructure.persistence.AccountDao;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CreateWalletService implements CreateWalletUseCase {

    private final AccountDao accountDao;

    public CreateWalletService(final AccountDao accountDao) {
        this.accountDao = accountDao;
    }


    @Traceable("wallet.create")
    @Override
    public UUID execute() {
        final UUID walletId = UUID.randomUUID();

        final var initialBalance = BigDecimal.ZERO;

        accountDao.insertAccount(walletId, initialBalance);

        return walletId;
    }



    @Override
    public UUID execute(@Nonnull final BigDecimal initialBalance) {
        final UUID walletId = UUID.randomUUID();

        accountDao.insertAccount(walletId, initialBalance);

        return walletId;
    }
}
