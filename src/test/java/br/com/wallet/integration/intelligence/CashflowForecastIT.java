package br.com.wallet.integration.intelligence;

import br.com.wallet.goals.api.CashflowProfileUseCase;
import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import br.com.wallet.intelligence.api.dto.CashflowSyncResponse;
import br.com.wallet.intelligence.api.event.CashflowShortfallAlertEvent;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.CashflowStatus;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import br.com.wallet.intelligence.internal.service.CashflowForecastingService;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({IntegrationTestBase.class, CashflowForecastIT.AlertCaptureConfig.class})
@DisplayName("CashflowForecastIT: End-to-End Forward Cashflow Forecasting & Goals Integration (TASK-3.2.11, TASK-3.2.12)")
class CashflowForecastIT extends DockerProperties {

    @Autowired
    private CashflowForecastingService forecastingService;

    @Autowired
    private SubscriptionDao subscriptionDao;

    @Autowired
    private CashflowProfileUseCase cashflowProfileUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShortfallAlertCapture capture;

    private final String tenantA = "tenant-alpha";
    private final String tenantB = "tenant-beta";

    @BeforeEach
    void setUp() {
        cleaner.clean();
        capture.clear();
    }

    @Test
    @DisplayName("REQ-CASH-001..005, I-CASH-001..007: Multiple occurrences, shortfall, installment exclusion, non-locking read, and tenant isolation")
    void testEndToEndCashflowForecastingAndGoalsSync() {
        UUID walletA = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID counterparty1 = UUID.randomUUID();
        UUID counterparty2 = UUID.randomUUID();

        UUID walletB = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // 1. Seed Accounts directly in DB
        jdbcTemplate.update("""
            INSERT INTO accounts (id, user_id, balance, version, status, tenant_id, created_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, NOW()),
                   (?, ?, ?, ?, 'ACTIVE', ?, NOW())
        """, walletA, userA, new BigDecimal("150.00"), 1L, tenantA,
             walletB, userB, new BigDecimal("1000.00"), 1L, tenantB);

        // 2. Seed Subscriptions in Tenant A:
        // Weekly subscription R$ 50.00 (nextExpectedAt = now + 7 days)
        Subscription subWeekly = new Subscription(
                UUID.randomUUID(), tenantA, walletA, counterparty1,
                Cadence.WEEKLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL, "SUBSCRIPTION",
                new BigDecimal("50.00"), new BigDecimal("50.00"), BigDecimal.ONE, 3,
                VarianceType.FIXED, now.plus(7, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS),
                now.minus(28, ChronoUnit.DAYS), now
        );
        subscriptionDao.upsert(subWeekly);

        // Monthly installment R$ 40.00 (nextExpectedAt = now + 10 days, classification = "INSTALLMENT")
        Subscription subInstallment = new Subscription(
                UUID.randomUUID(), tenantA, walletA, counterparty2,
                Cadence.MONTHLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL, "INSTALLMENT",
                new BigDecimal("40.00"), new BigDecimal("40.00"), BigDecimal.ONE, 2,
                VarianceType.FIXED, now.plus(10, ChronoUnit.DAYS), now.minus(20, ChronoUnit.DAYS),
                now.minus(50, ChronoUnit.DAYS), now
        );
        subscriptionDao.upsert(subInstallment);

        // Seed Subscription in Tenant B (should NOT bleed into Tenant A):
        Subscription subTenantB = new Subscription(
                UUID.randomUUID(), tenantB, walletB, UUID.randomUUID(),
                Cadence.WEEKLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL, "SUBSCRIPTION",
                new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ONE, 3,
                VarianceType.FIXED, now.plus(7, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS),
                now.minus(28, ChronoUnit.DAYS), now
        );
        subscriptionDao.upsert(subTenantB);

        // 3. Evaluate Projections for Tenant A
        CashflowProjectionResponse projectionA = forecastingService.getProjections(tenantA, walletA);

        assertThat(projectionA.tenantId()).isEqualTo(tenantA);
        assertThat(projectionA.walletId()).isEqualTo(walletA);
        assertThat(projectionA.currentBalance()).isEqualByComparingTo("150.00");

        // 7d: 1 weekly occurrence (R$ 50.00) = R$ 50.00
        assertThat(projectionA.liabilities7Days()).isEqualByComparingTo("50.00");
        // 14d: 2 weekly occurrences (R$ 100.00) + 1 installment (R$ 40.00) = R$ 140.00
        assertThat(projectionA.liabilities14Days()).isEqualByComparingTo("140.00");
        // 30d: 4 weekly occurrences (R$ 200.00) + 1 installment (R$ 40.00) = R$ 240.00
        assertThat(projectionA.liabilities30Days()).isEqualByComparingTo("240.00");

        // Shortfall 14d: max(0, 140 - 150) = 0.00
        assertThat(projectionA.shortfall14Days()).isEqualByComparingTo("0.00");
        // Shortfall 30d: max(0, 240 - 150) = 90.00
        assertThat(projectionA.shortfall30Days()).isEqualByComparingTo("90.00");
        assertThat(projectionA.status30Days()).isEqualTo(CashflowStatus.DEFICIT_WARNING);

        // Installment partitioning:
        // Installment excluded from monthlyCommittedExpenses: 50.00 * 4.33 = 216.50
        assertThat(projectionA.normalizedMonthlyCommitted()).isEqualByComparingTo("216.50");
        assertThat(projectionA.activeInstallmentsCount()).isEqualTo(1);

        // 4. Invariant I-CASH-006 & I-INTEL-001: Non-locking read, zero ledger mutations
        Long accountVersion = jdbcTemplate.queryForObject("SELECT version FROM accounts WHERE id = ?", Long.class, walletA);
        assertThat(accountVersion).isEqualTo(1L);
        Integer ledgerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger WHERE wallet_id = ?", Integer.class, walletA);
        assertThat(ledgerCount).isZero();

        // 5. Tenant B Isolation: Tenant B forecast is isolated
        CashflowProjectionResponse projectionB = forecastingService.getProjections(tenantB, walletB);
        assertThat(projectionB.tenantId()).isEqualTo(tenantB);
        assertThat(projectionB.currentBalance()).isEqualByComparingTo("1000.00");
        assertThat(projectionB.liabilities30Days()).isEqualByComparingTo("2000.00"); // 500 * 4

        // 6. Synchronize with Goals
        CashflowSyncResponse syncResponse = forecastingService.syncGoals(tenantA, walletA);
        assertThat(syncResponse.walletId()).isEqualTo(walletA);
        assertThat(syncResponse.userId()).isEqualTo(userA);
        assertThat(syncResponse.monthlyCommittedExpenses()).isEqualByComparingTo("216.50");
        assertThat(syncResponse.monthlyIncome()).isEqualByComparingTo("0.00");
        assertThat(syncResponse.minimumSafetyBuffer()).isEqualByComparingTo("0.00");
        assertThat(syncResponse.synced()).isTrue();

        // Verify in Goals DB via CashflowProfileUseCase
        Optional<CashflowProfile> profileOpt = cashflowProfileUseCase.getCashflowProfileByWalletId(walletA);
        assertThat(profileOpt).isPresent();
        CashflowProfile profile = profileOpt.get();
        assertThat(profile.monthlyCommittedExpenses()).isEqualByComparingTo("216.50");

        // 7. Idempotent Sync: running sync again produces identical values
        CashflowSyncResponse syncResponse2 = forecastingService.syncGoals(tenantA, walletA);
        assertThat(syncResponse2.monthlyCommittedExpenses()).isEqualByComparingTo("216.50");
    }

    @Test
    @DisplayName("REQ-CASH-006 [SHOULD]: Emits CashflowShortfallAlertEvent when 14-day shortfall exists during sync")
    void shouldEmitShortfallAlertEventWhen14DayShortfallOccurs() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Account with low balance: R$ 30.00
        jdbcTemplate.update("""
            INSERT INTO accounts (id, user_id, balance, version, status, tenant_id, created_at)
            VALUES (?, ?, ?, 1, 'ACTIVE', ?, NOW())
        """, walletId, userId, new BigDecimal("30.00"), tenantA);

        // Subscription: weekly R$ 50.00 (14-day liabilities = R$ 100.00 > R$ 30.00 -> Shortfall = R$ 70.00)
        Subscription sub = new Subscription(
                UUID.randomUUID(), tenantA, walletId, UUID.randomUUID(),
                Cadence.WEEKLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL, "SUBSCRIPTION",
                new BigDecimal("50.00"), new BigDecimal("50.00"), BigDecimal.ONE, 3,
                VarianceType.FIXED, now.plus(7, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS),
                now.minus(28, ChronoUnit.DAYS), now
        );
        subscriptionDao.upsert(sub);

        // Perform sync
        forecastingService.syncGoals(tenantA, walletId);

        // Assert shortfall event was captured
        assertThat(capture.getEvents()).hasSize(1);
        CashflowShortfallAlertEvent alert = capture.getEvents().getFirst();
        assertThat(alert.tenantId()).isEqualTo(tenantA);
        assertThat(alert.walletId()).isEqualTo(walletId);
        assertThat(alert.currentBalance()).isEqualByComparingTo("30.00");
        assertThat(alert.liabilities14Days()).isEqualByComparingTo("100.00");
        assertThat(alert.shortfall14Days()).isEqualByComparingTo("70.00");
    }

    @TestConfiguration
    static class AlertCaptureConfig {
        @Bean
        public ShortfallAlertCapture shortfallAlertCapture() {
            return new ShortfallAlertCapture();
        }
    }

    public static class ShortfallAlertCapture {
        private final List<CashflowShortfallAlertEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onAlert(CashflowShortfallAlertEvent event) {
            events.add(event);
        }

        public List<CashflowShortfallAlertEvent> getEvents() {
            return events;
        }

        public void clear() {
            events.clear();
        }
    }
}
