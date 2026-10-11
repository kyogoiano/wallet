package br.com.wallet.intelligence.internal.service;

import br.com.wallet.intelligence.api.SubscriptionQueryUseCase;
import br.com.wallet.intelligence.api.dto.SubscriptionResponse;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.engine.RecurrencePatternEngine;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionQueryServiceTest {

    @Mock
    private SubscriptionDao subscriptionDao;

    private Clock clock;
    private RecurrencePatternEngine recurrenceEngine;
    private SubscriptionQueryUseCase useCase;

    private final String tenantId = "tenant-alpha";
    private final UUID walletId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        recurrenceEngine = new RecurrencePatternEngine();
        useCase = new SubscriptionQueryService(subscriptionDao, recurrenceEngine, clock);
    }

    @Test
    @DisplayName("Positive: getSubscriptions returns active subscriptions mapped to DTO")
    void testGetSubscriptionsPositive() {
        UUID subId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();
        Subscription sub = new Subscription(
                subId, tenantId, walletId, counterpartyId,
                Cadence.MONTHLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL,
                "STREAMING", new BigDecimal("49.90"), new BigDecimal("49.90"),
                new BigDecimal("0.95"), 5, VarianceType.FIXED,
                now.plusSeconds(86400 * 15), now.minusSeconds(86400 * 15),
                now.minusSeconds(86400 * 150), now
        );

        when(subscriptionDao.findByWalletId(tenantId, walletId, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(sub));

        List<SubscriptionResponse> results = useCase.getSubscriptions(tenantId, walletId, SubscriptionStatus.ACTIVE);

        assertThat(results).hasSize(1);
        SubscriptionResponse resp = results.getFirst();
        assertThat(resp.id()).isEqualTo(subId);
        assertThat(resp.tenantId()).isEqualTo(tenantId);
        assertThat(resp.walletId()).isEqualTo(walletId);
        assertThat(resp.averageAmount()).isEqualByComparingTo(new BigDecimal("49.90"));
        assertThat(resp.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Boundary: returns empty list when no subscriptions exist for tenant")
    void testGetSubscriptionsEmptyBoundary() {
        when(subscriptionDao.findByWalletId(tenantId, walletId, null))
                .thenReturn(List.of());

        List<SubscriptionResponse> results = useCase.getSubscriptions(tenantId, walletId, null);

        assertThat(results).isEmpty();
    }
}
