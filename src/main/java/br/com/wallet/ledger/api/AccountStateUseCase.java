package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.domain.AccountStatus;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public interface AccountStateUseCase {
    void blockAccount(@NonNull UUID walletId, @NonNull String reason);
    void unblockAccount(@NonNull UUID walletId);
    Optional<AccountStatus> getAccountStatus(@NonNull UUID walletId);
}
