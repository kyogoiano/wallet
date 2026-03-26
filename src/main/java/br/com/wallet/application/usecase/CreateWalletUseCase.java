package br.com.wallet.application.usecase;


import br.com.wallet.domain.context.Wallet;

import java.util.UUID;

public interface CreateWalletUseCase {
    UUID execute(Wallet wallet);
    UUID execute();
}