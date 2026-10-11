package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.domain.LedgerValidationResult;

import java.util.UUID;

public interface ValidateLedgerUseCase {
    LedgerValidationResult execute(UUID walletId);
}
