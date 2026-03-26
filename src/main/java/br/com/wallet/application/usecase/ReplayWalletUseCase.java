package br.com.wallet.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReplayWalletUseCase {
    BigDecimal execute(UUID walletId);
}
