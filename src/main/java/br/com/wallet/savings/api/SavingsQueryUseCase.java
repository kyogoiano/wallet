package br.com.wallet.savings.api;

import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import java.util.UUID;

public interface SavingsQueryUseCase {
    SavingsMetricsResponse getMetrics(UUID walletId);
}
