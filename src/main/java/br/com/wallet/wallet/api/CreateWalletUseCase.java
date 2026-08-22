package br.com.wallet.wallet.api;


import br.com.wallet.wallet.api.context.Wallet;

import java.util.UUID;

public interface CreateWalletUseCase extends UseCase<Wallet> {
    @Override
    void handle(Wallet wallet);
    void handle(UUID walletId, UUID userId);
}