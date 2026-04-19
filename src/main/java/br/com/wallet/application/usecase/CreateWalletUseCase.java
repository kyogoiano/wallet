package br.com.wallet.application.usecase;


import br.com.wallet.domain.context.Wallet;

import java.util.UUID;

public interface CreateWalletUseCase extends UseCase<Wallet> {
    @Override
    void handle(Wallet wallet);
    void handle(UUID walletId);
}