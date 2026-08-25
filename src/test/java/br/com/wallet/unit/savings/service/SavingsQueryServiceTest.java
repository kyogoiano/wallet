package br.com.wallet.unit.savings.service;

import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import br.com.wallet.savings.internal.application.SavingsQueryService;
import br.com.wallet.savings.internal.persistence.SavingsExecutionHistoryDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavingsQueryService Unit Tests")
class SavingsQueryServiceTest {

    @Mock
    private SavingsExecutionHistoryDao historyDao;

    @InjectMocks
    private SavingsQueryService queryService;

    private final UUID walletId = UUID.randomUUID();

    @Test
    @DisplayName("Should aggregate savings metrics correctly")
    void shouldAggregateSavingsMetrics() {
        when(historyDao.getTotalSavedByWalletId(walletId)).thenReturn(new BigDecimal("1250.50"));
        when(historyDao.getExecutionCountByWalletId(walletId)).thenReturn(15L);
        when(historyDao.getSavedBreakdownByRuleType(walletId)).thenReturn(Map.of(
                "PERCENTAGE", new BigDecimal("1000.00"),
                "ROUND_UP", new BigDecimal("250.50")
        ));

        SavingsMetricsResponse metrics = queryService.getMetrics(walletId);

        assertThat(metrics).isNotNull();
        assertThat(metrics.walletId()).isEqualTo(walletId);
        assertThat(metrics.totalSaved()).isEqualByComparingTo("1250.50");
        assertThat(metrics.executionCount()).isEqualTo(15L);
        assertThat(metrics.savedByRuleType()).containsEntry("PERCENTAGE", new BigDecimal("1000.00"));
        assertThat(metrics.savedByRuleType()).containsEntry("ROUND_UP", new BigDecimal("250.50"));
    }
}
