package br.com.wallet.goals.api;

import br.com.wallet.goals.api.dto.SaveCashflowProfileCommand;
import br.com.wallet.goals.api.model.CashflowProfile;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public interface CashflowProfileUseCase {
    @NonNull
    CashflowProfile saveCashflowProfile(@NonNull SaveCashflowProfileCommand command);

    @NonNull
    Optional<CashflowProfile> getCashflowProfileByWalletId(@NonNull UUID walletId);
}
