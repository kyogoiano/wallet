package br.com.wallet.ledger.api;


import br.com.wallet.ledger.api.context.Wallet;

import java.util.UUID;

public interface CreateWalletUseCase extends UseCase<Wallet> {
    @Override
    void handle(Wallet wallet);
    void handle(UUID walletId, UUID userId);
}