package br.com.wallet.integration.intelligence;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.intelligence.api.event.SubscriptionPriceSpikeEvent;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import({IntegrationTestBase.class, SubscriptionDetectionIT.SpikeCaptureConfig.class})
@DisplayName("SubscriptionDetectionIT: End-to-End Recurring Pattern Detection (TASK-3.1.13, REQ-SUB-003, REQ-SUB-006, I-SUB-003, I-SUB-010)")
class SubscriptionDetectionIT extends DockerProperties {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private SubscriptionDao subscriptionDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpikeEventCapture capture;

    @Autowired
    private br.com.wallet.intelligence.internal.rest.SubscriptionController subscriptionController;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        capture.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("REQ-SUB-003, REQ-SUB-006, I-SUB-003, I-SUB-010: 3 monthly transfers promote to ACTIVE, 4th with +15% price spike emits verified event while ACTIVE")
    void shouldDetectMonthlySubscriptionAndPriceSpikeWithTenantIsolation() {
        String tenantA = "tenant-alpha";
        String tenantB = "tenant-beta";
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        BigDecimal fixedAmount = new BigDecimal("50.00");

        // 1st Monthly Transfer
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new TransferCompletedEvent(
                    from, to, fixedAmount, UUID.randomUUID(), OperationOrigin.USER, tenantA
            ));
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Subscription> s = subscriptionDao.findBySeries(tenantA, from, to);
            assertThat(s).isPresent();
            assertThat(s.get().observedCycles()).isEqualTo(1);
            assertThat(s.get().status()).isEqualTo(SubscriptionStatus.DISCOVERED);
        });

        // Simulate passage of 30 days: shift created_at and last_observed_at back by 60 days
        jdbcTemplate.update(
                "UPDATE subscriptions SET created_at = NOW() - INTERVAL '60 days', last_observed_at = NOW() - INTERVAL '60 days' " +
                        "WHERE tenant_id = ? AND wallet_id = ? AND counterparty_id = ?",
                tenantA, from, to
        );

        // 2nd Monthly Transfer
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new TransferCompletedEvent(
                    from, to, fixedAmount, UUID.randomUUID(), OperationOrigin.USER, tenantA
            ));
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Subscription> s = subscriptionDao.findBySeries(tenantA, from, to);
            assertThat(s).isPresent();
            assertThat(s.get().observedCycles()).isEqualTo(2);
            assertThat(s.get().status()).isEqualTo(SubscriptionStatus.CANDIDATE);
        });

        // Simulate passage of another 30 days: shift last_observed_at to 30 days ago
        jdbcTemplate.update(
                "UPDATE subscriptions SET last_observed_at = NOW() - INTERVAL '30 days' " +
                        "WHERE tenant_id = ? AND wallet_id = ? AND counterparty_id = ?",
                tenantA, from, to
        );

        // 3rd Monthly Transfer
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new TransferCompletedEvent(
                    from, to, fixedAmount, UUID.randomUUID(), OperationOrigin.USER, tenantA
            ));
        });

        // Verify promotion to ACTIVE, MONTHLY cadence, Confidence >= 0.70
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Subscription> s = subscriptionDao.findBySeries(tenantA, from, to);
            assertThat(s).isPresent();
            Subscription sub = s.get();
            assertThat(sub.observedCycles()).isEqualTo(3);
            assertThat(sub.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(sub.cadence()).isEqualTo(Cadence.MONTHLY);
            assertThat(sub.confidence()).isGreaterThanOrEqualTo(new BigDecimal("0.700000"));
            assertThat(sub.priceState()).isEqualTo(PriceState.NORMAL);
            assertThat(sub.averageAmount()).isEqualByComparingTo(fixedAmount);
            assertThat(sub.nextExpectedAt()).isNotNull();
        });

        // 4th Monthly Transfer with +15% price spike (57.50 vs baseline 50.00)
        UUID spikeOpId = UUID.randomUUID();
        BigDecimal spikedAmount = new BigDecimal("57.50");
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new TransferCompletedEvent(
                    from, to, spikedAmount, spikeOpId, OperationOrigin.USER, tenantA
            ));
        });

        // Verify priceState transitions to PRICE_SPIKE_DETECTED while status remains ACTIVE
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Subscription> s = subscriptionDao.findBySeries(tenantA, from, to);
            assertThat(s).isPresent();
            Subscription sub = s.get();
            assertThat(sub.observedCycles()).isEqualTo(4);
            assertThat(sub.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(sub.priceState()).isEqualTo(PriceState.PRICE_SPIKE_DETECTED);
            assertThat(sub.lastAmount()).isEqualByComparingTo(spikedAmount);

            // Semantically verify SubscriptionPriceSpikeEvent
            assertThat(capture.getEvents()).hasSize(1);
            SubscriptionPriceSpikeEvent event = capture.getEvents().getFirst();
            assertThat(event.tenantId()).isEqualTo(tenantA);
            assertThat(event.walletId()).isEqualTo(from);
            assertThat(event.counterpartyId()).isEqualTo(to);
            assertThat(event.baselineAmount()).isEqualByComparingTo(fixedAmount);
            assertThat(event.observedAmount()).isEqualByComparingTo(spikedAmount);
            assertThat(event.percentageIncrease()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(event.triggerEventId()).isEqualTo(spikeOpId);
        });

        // Multi-Tenant Isolation Check (I-SUB-010): Tenant B transaction does not alter Tenant A series
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new TransferCompletedEvent(
                    from, to, fixedAmount, UUID.randomUUID(), OperationOrigin.USER, tenantB
            ));
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Subscription> subB = subscriptionDao.findBySeries(tenantB, from, to);
            assertThat(subB).isPresent();
            assertThat(subB.get().tenantId()).isEqualTo(tenantB);
            assertThat(subB.get().observedCycles()).isEqualTo(1);

            // Tenant A series remains completely unaffected with 4 cycles
            Optional<Subscription> subA = subscriptionDao.findBySeries(tenantA, from, to);
            assertThat(subA).isPresent();
            assertThat(subA.get().observedCycles()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("REQ-SUB-009, I-SUB-004: Elapsed > 1.50 * Δt infers CANCELLED_INFERRED, subsequent transfer reactivates to CANDIDATE")
    void shouldInferCancellationAndReactivateToCandidate() {
        String tenantId = "tenant-gamma";
        UUID walletId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        java.time.Instant t0 = java.time.Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS);
        java.time.Instant tLast = java.time.Instant.now().minus(60, java.time.temporal.ChronoUnit.DAYS);

        // Seed an ACTIVE subscription whose last observation was 60 days ago (> 1.50 * 30 = 45 days)
        Subscription activeSub = new Subscription(
                UUID.randomUUID(), tenantId, walletId, cpId,
                Cadence.MONTHLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL,
                "STREAMING", new BigDecimal("30.00"), new BigDecimal("30.00"),
                new BigDecimal("1.000000"), 3, br.com.wallet.intelligence.api.model.VarianceType.FIXED,
                tLast.plus(30, java.time.temporal.ChronoUnit.DAYS), tLast, t0, tLast
        );
        subscriptionDao.upsert(activeSub);

        // Query via controller with mock request containing tenantId header
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", tenantId);

        var response = subscriptionController.getSubscriptions(walletId, null, request);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().getFirst().status()).isEqualTo(SubscriptionStatus.CANCELLED_INFERRED);

        // Verify status in database is now CANCELLED_INFERRED
        Optional<Subscription> cancelledDb = subscriptionDao.findBySeries(tenantId, walletId, cpId);
        assertThat(cancelledDb).isPresent();
        assertThat(cancelledDb.get().status()).isEqualTo(SubscriptionStatus.CANCELLED_INFERRED);

        // New qualifying transfer arrives for this cancelled series
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new TransferCompletedEvent(
                    walletId, cpId, new BigDecimal("30.00"), UUID.randomUUID(), OperationOrigin.USER, tenantId
            ));
        });

        // Verify subscription reactivates to CANDIDATE over full history
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Subscription> reactivated = subscriptionDao.findBySeries(tenantId, walletId, cpId);
            assertThat(reactivated).isPresent();
            assertThat(reactivated.get().status()).isEqualTo(SubscriptionStatus.CANDIDATE);
            assertThat(reactivated.get().observedCycles()).isEqualTo(4);
        });
    }

    @TestConfiguration
    static class SpikeCaptureConfig {
        @Bean
        public SpikeEventCapture spikeEventCapture() {
            return new SpikeEventCapture();
        }
    }

    public static class SpikeEventCapture {
        private final List<SubscriptionPriceSpikeEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onPriceSpike(SubscriptionPriceSpikeEvent event) {
            events.add(event);
        }

        public List<SubscriptionPriceSpikeEvent> getEvents() {
            return events;
        }

        public void clear() {
            events.clear();
        }
    }
}
