package br.com.wallet.application.usecase;

import br.com.wallet.domain.LedgerValidationResult;

import java.util.UUID;

public interface ValidateLedgerUseCase {
    LedgerValidationResult execute(UUID walletId);
}
