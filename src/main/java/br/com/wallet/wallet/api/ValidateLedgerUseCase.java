package br.com.wallet.wallet.api;

import br.com.wallet.wallet.api.domain.LedgerValidationResult;

import java.util.UUID;

public interface ValidateLedgerUseCase {
    LedgerValidationResult execute(UUID walletId);
}
