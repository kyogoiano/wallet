package br.com.wallet.ledger.internal.service;

import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.ledger.api.AccountStateUseCase;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountStateService implements AccountStateUseCase {

    private static final Logger log = LoggerFactory.getLogger(AccountStateService.class);

    private final AccountDao accountDao;
    private final FraudService fraudService;

    public AccountStateService(
            @NonNull final AccountDao accountDao,
            @NonNull final FraudService fraudService
    ) {
        this.accountDao = Objects.requireNonNull(accountDao, "accountDao cannot be null");
        this.fraudService = Objects.requireNonNull(fraudService, "fraudService cannot be null");
    }

    @Override
    @Transactional
    public void blockAccount(@NonNull final UUID walletId, @NonNull final String reason) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        log.warn("Blocking account: walletId={}, reason={}", walletId, reason);
        accountDao.blockAccount(walletId, reason);

        accountDao.findUserId(walletId).ifPresent(fraudService::blockUser);
    }

    @Override
    @Transactional
    public void unblockAccount(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");

        log.info("Unblocking account: walletId={}", walletId);
        accountDao.unblockAccount(walletId);

        accountDao.findUserId(walletId).ifPresent(fraudService::unblockUser);
    }

    @Override
    public Optional<AccountStatus> getAccountStatus(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return accountDao.findAccountStatus(walletId);
    }
}
