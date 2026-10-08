package br.com.wallet.intelligence.internal.persistence;

import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("SubscriptionDao Tests (TASK-3.1.3, REQ-SUB-002, REQ-SUB-007, I-SUB-008, I-SUB-010)")
class SubscriptionDaoTest extends DockerProperties {

    @Autowired
    private SubscriptionDao subscriptionDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    @DisplayName("I-SUB-008: tryRecordProcessedEvent enforces durable idempotency across duplicate event IDs")
    void shouldEnforceDurableIdempotencyOnProcessedEvents() {
        UUID eventId = UUID.randomUUID();
        String tenantId = "tenant-test";

        boolean firstAttempt = subscriptionDao.tryRecordProcessedEvent(eventId, tenantId);
        boolean secondAttempt = subscriptionDao.tryRecordProcessedEvent(eventId, tenantId);
        boolean differentEvent = subscriptionDao.tryRecordProcessedEvent(UUID.randomUUID(), tenantId);

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
        assertThat(differentEvent).isTrue();
    }

    @Test
    @DisplayName("REQ-SUB-002, REQ-SUB-007: upsert persists new subscription and findBySeries retrieves all fields")
    void shouldUpsertAndFindSubscriptionBySeries() {
        UUID id = UUID.randomUUID();
        String tenantId = "tenant-alpha";
        UUID walletId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant nextExpected = now.plus(30, ChronoUnit.DAYS);

        Subscription subscription = new Subscription(
                id,
                tenantId,
                walletId,
                counterpartyId,
                Cadence.MONTHLY,
                SubscriptionStatus.ACTIVE,
                PriceState.NORMAL,
                "STREAMING_ENTERTAINMENT",
                new BigDecimal("49.90"),
                new BigDecimal("49.90"),
                new BigDecimal("1.00"),
                3,
                VarianceType.FIXED,
                nextExpected,
                now,
                now,
                now
        );

        subscriptionDao.upsert(subscription);

        Optional<Subscription> found = subscriptionDao.findBySeries(tenantId, walletId, counterpartyId);
        assertThat(found).isPresent();
        Subscription loaded = found.get();
        assertThat(loaded.id()).isEqualTo(id);
        assertThat(loaded.tenantId()).isEqualTo(tenantId);
        assertThat(loaded.walletId()).isEqualTo(walletId);
        assertThat(loaded.counterpartyId()).isEqualTo(counterpartyId);
        assertThat(loaded.cadence()).isEqualTo(Cadence.MONTHLY);
        assertThat(loaded.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(loaded.priceState()).isEqualTo(PriceState.NORMAL);
        assertThat(loaded.classification()).isEqualTo("STREAMING_ENTERTAINMENT");
        assertThat(loaded.averageAmount()).isEqualByComparingTo(new BigDecimal("49.90"));
        assertThat(loaded.lastAmount()).isEqualByComparingTo(new BigDecimal("49.90"));
        assertThat(loaded.confidence()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(loaded.observedCycles()).isEqualTo(3);
        assertThat(loaded.varianceType()).isEqualTo(VarianceType.FIXED);
        assertThat(loaded.nextExpectedAt()).isNotNull();
    }

    @Test
    @DisplayName("REQ-SUB-002: upsert updates existing subscription record on conflict without creating duplicate rows")
    void shouldUpdateExistingSubscriptionOnConflict() {
        UUID id = UUID.randomUUID();
        String tenantId = "tenant-beta";
        UUID walletId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();
        Instant t1 = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Subscription initial = new Subscription(
                id,
                tenantId,
                walletId,
                counterpartyId,
                Cadence.MONTHLY,
                SubscriptionStatus.CANDIDATE,
                PriceState.NORMAL,
                "SAAS_SUBSCRIPTION",
                new BigDecimal("29.90"),
                new BigDecimal("29.90"),
                new BigDecimal("0.66"),
                2,
                VarianceType.FIXED,
                null,
                t1,
                t1,
                t1
        );
        subscriptionDao.upsert(initial);

        Instant t2 = t1.plus(30, ChronoUnit.DAYS);
        Subscription updated = new Subscription(
                id,
                tenantId,
                walletId,
                counterpartyId,
                Cadence.MONTHLY,
                SubscriptionStatus.ACTIVE,
                PriceState.PRICE_SPIKE_DETECTED,
                "SAAS_SUBSCRIPTION",
                new BigDecimal("32.50"),
                new BigDecimal("35.00"),
                new BigDecimal("1.00"),
                3,
                VarianceType.VARIABLE,
                t2.plus(30, ChronoUnit.DAYS),
                t2,
                t1,
                t2
        );
        subscriptionDao.upsert(updated);

        Optional<Subscription> found = subscriptionDao.findBySeries(tenantId, walletId, counterpartyId);
        assertThat(found).isPresent();
        Subscription loaded = found.get();
        assertThat(loaded.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(loaded.priceState()).isEqualTo(PriceState.PRICE_SPIKE_DETECTED);
        assertThat(loaded.observedCycles()).isEqualTo(3);
        assertThat(loaded.lastAmount()).isEqualByComparingTo(new BigDecimal("35.00"));
        assertThat(loaded.varianceType()).isEqualTo(VarianceType.VARIABLE);
    }

    @Test
    @DisplayName("I-SUB-010: findByWalletId enforces strict tenant partitioning and status filtering")
    void shouldEnforceTenantIsolationAndStatusFiltering() {
        String tenantA = "tenant-A";
        String tenantB = "tenant-B";
        UUID walletId = UUID.randomUUID();
        UUID cp1 = UUID.randomUUID();
        UUID cp2 = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Subscription sub1 = new Subscription(
                UUID.randomUUID(), tenantA, walletId, cp1,
                Cadence.MONTHLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL,
                "CLASSIF_1", new BigDecimal("10.00"), new BigDecimal("10.00"),
                new BigDecimal("1.00"), 3, VarianceType.FIXED, null, now, now, now
        );
        Subscription sub2 = new Subscription(
                UUID.randomUUID(), tenantA, walletId, cp2,
                Cadence.WEEKLY, SubscriptionStatus.DISCOVERED, PriceState.NORMAL,
                "CLASSIF_2", new BigDecimal("20.00"), new BigDecimal("20.00"),
                new BigDecimal("0.33"), 1, VarianceType.FIXED, null, now, now, now
        );
        subscriptionDao.upsert(sub1);
        subscriptionDao.upsert(sub2);

        // Tenant A sees both when unfiltered
        List<Subscription> allTenantA = subscriptionDao.findByWalletId(tenantA, walletId, null);
        assertThat(allTenantA).hasSize(2);

        // Tenant A sees only ACTIVE when filtered
        List<Subscription> activeTenantA = subscriptionDao.findByWalletId(tenantA, walletId, SubscriptionStatus.ACTIVE);
        assertThat(activeTenantA).hasSize(1);
        assertThat(activeTenantA.getFirst().counterpartyId()).isEqualTo(cp1);

        // Tenant B with same walletId sees nothing
        List<Subscription> tenantBResults = subscriptionDao.findByWalletId(tenantB, walletId, null);
        assertThat(tenantBResults).isEmpty();
    }
}
