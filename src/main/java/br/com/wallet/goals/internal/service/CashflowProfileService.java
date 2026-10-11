package br.com.wallet.goals.internal.service;

import br.com.wallet.goals.api.CashflowProfileUseCase;
import br.com.wallet.goals.api.dto.SaveCashflowProfileCommand;
import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.goals.internal.persistence.CashflowProfileDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CashflowProfileService implements CashflowProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(CashflowProfileService.class);
    private final CashflowProfileDao cashflowProfileDao;

    public CashflowProfileService(@NonNull final CashflowProfileDao cashflowProfileDao) {
        this.cashflowProfileDao = Objects.requireNonNull(cashflowProfileDao, "cashflowProfileDao cannot be null");
    }

    @Override
    @Transactional
    @NonNull
    public CashflowProfile saveCashflowProfile(@NonNull final SaveCashflowProfileCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        final UUID id = cashflowProfileDao.findByWalletId(command.walletId())
                .map(CashflowProfile::id)
                .orElseGet(UUID::randomUUID);

        final CashflowProfile profile = new CashflowProfile(
                id,
                command.userId(),
                command.walletId(),
                command.monthlyIncome(),
                command.monthlyCommittedExpenses(),
                command.minimumSafetyBuffer(),
                Instant.now()
        );

        cashflowProfileDao.upsert(profile);
        log.info("Saved cashflow profile for wallet: {}", command.walletId());
        return profile;
    }

    @Override
    @Transactional(readOnly = true)
    @NonNull
    public Optional<CashflowProfile> getCashflowProfileByWalletId(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return cashflowProfileDao.findByWalletId(walletId);
    }
}
