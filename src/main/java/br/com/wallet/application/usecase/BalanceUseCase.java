package br.com.wallet.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface BalanceUseCase {
    BigDecimal getBalance(UUID walletId);
    BigDecimal getHistoricalBalance(UUID walletId, Instant createdAt);
}
