package br.com.wallet.ledger.internal.service;

import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService implements AccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountDao accountDao;

    public AccountService(final AccountDao accountDao) {
        this.accountDao = accountDao;
    }


    @Override
    public @NonNull Account find(@NonNull final UUID walletId) {
        log.info("Retrieving account for wallet {}", walletId);
        return accountDao.findAccount(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    @Override
    public @NonNull List<@NonNull Account> list(@NonNull final Integer limit, @NonNull final Integer offset) {
        log.info("Retrieving account page, size {}, offset: {}", limit, offset);
        return accountDao.listAccounts(limit, offset);
    }
}
