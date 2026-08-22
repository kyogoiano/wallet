package br.com.wallet.wallet.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReplayWalletUseCase {
    BigDecimal execute(UUID walletId);
}
