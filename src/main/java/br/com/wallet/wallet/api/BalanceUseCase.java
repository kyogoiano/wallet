package br.com.wallet.wallet.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface BalanceUseCase {
    BigDecimal getBalance(UUID walletId);
    BigDecimal getHistoricalBalance(UUID walletId, Instant createdAt);
}
