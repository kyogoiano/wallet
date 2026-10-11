package br.com.wallet.savings.internal.application;

import br.com.wallet.savings.api.SavingsQueryUseCase;
import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import br.com.wallet.savings.internal.persistence.SavingsExecutionHistoryDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SavingsQueryService implements SavingsQueryUseCase {

    private final SavingsExecutionHistoryDao historyDao;

    public SavingsQueryService(@NonNull final SavingsExecutionHistoryDao historyDao) {
        this.historyDao = Objects.requireNonNull(historyDao, "historyDao cannot be null");
    }

    @Override
    public SavingsMetricsResponse getMetrics(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");

        BigDecimal totalSaved = historyDao.getTotalSavedByWalletId(walletId);
        long executionCount = historyDao.getExecutionCountByWalletId(walletId);
        Map<String, BigDecimal> breakdown = historyDao.getSavedBreakdownByRuleType(walletId);

        return new SavingsMetricsResponse(
                walletId,
                totalSaved,
                executionCount,
                breakdown
        );
    }
}
