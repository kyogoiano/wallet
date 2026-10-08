package br.com.wallet.intelligence.internal.service;

import br.com.wallet.goals.api.CashflowProfileUseCase;
import br.com.wallet.goals.api.dto.SaveCashflowProfileCommand;
import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import br.com.wallet.intelligence.api.dto.CashflowSyncResponse;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.CashflowStatus;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.engine.CashflowForecastingEngine;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashflowForecastingServiceTest {

    @Mock
    private SubscriptionDao subscriptionDao;
    @Mock
    private BalanceUseCase balanceUseCase;
    @Mock
    private AccountUseCase accountUseCase;
    @Mock
    private CashflowProfileUseCase cashflowProfileUseCase;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Clock clock;
    private CashflowForecastingEngine engine;
    private CashflowForecastingService service;

    private final String tenantId = "tenant-alpha";
    private final UUID walletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant fixedNow = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        engine = new CashflowForecastingEngine();
        service = new CashflowForecastingService(
                subscriptionDao,
                balanceUseCase,
                accountUseCase,
                cashflowProfileUseCase,
                engine,
                clock,
                eventPublisher
        );
    }

    @Test
    @DisplayName("getProjections delegates to non-locking balance read and active subscriptions (TASK-3.2.7)")
    void getProjectionsSuccess() {
        when(balanceUseCase.getBalance(walletId)).thenReturn(new BigDecimal("150.00"));

        Subscription sub = new Subscription(
                UUID.randomUUID(),
                tenantId,
                walletId,
                UUID.randomUUID(),
                Cadence.WEEKLY,
                SubscriptionStatus.ACTIVE,
                PriceState.NORMAL,
                "SUBSCRIPTION",
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                BigDecimal.ONE,
                3,
                VarianceType.FIXED,
                fixedNow.plus(7, ChronoUnit.DAYS),
                fixedNow.minus(30, ChronoUnit.DAYS),
                fixedNow.minus(90, ChronoUnit.DAYS),
                fixedNow
        );
        when(subscriptionDao.findByWalletId(tenantId, walletId, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(sub));

        CashflowProjectionResponse response = service.getProjections(tenantId, walletId);

        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.currentBalance()).isEqualByComparingTo("150.00");
        assertThat(response.liabilities14Days()).isEqualByComparingTo("100.00");
        assertThat(response.liabilities30Days()).isEqualByComparingTo("200.00");
        assertThat(response.shortfall14Days()).isEqualByComparingTo("0.00");
        assertThat(response.shortfall30Days()).isEqualByComparingTo("50.00");
        assertThat(response.status30Days()).isEqualTo(CashflowStatus.DEFICIT_WARNING);
        assertThat(response.normalizedMonthlyCommitted()).isEqualByComparingTo("216.50"); // 50 * 4.33
    }

    @Test
    @DisplayName("syncGoals preserves existing minimumSafetyBuffer and monthlyIncome when profile exists (I-CASH-004)")
    void syncGoalsPreservesExistingProfile() {
        Subscription sub = new Subscription(
                UUID.randomUUID(),
                tenantId,
                walletId,
                UUID.randomUUID(),
                Cadence.MONTHLY,
                SubscriptionStatus.ACTIVE,
                PriceState.NORMAL,
                "SUBSCRIPTION",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                BigDecimal.ONE,
                3,
                VarianceType.FIXED,
                fixedNow.plus(15, ChronoUnit.DAYS),
                fixedNow.minus(30, ChronoUnit.DAYS),
                fixedNow.minus(90, ChronoUnit.DAYS),
                fixedNow
        );
        when(subscriptionDao.findByWalletId(tenantId, walletId, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(sub));

        CashflowProfile existing = new CashflowProfile(
                UUID.randomUUID(),
                userId,
                walletId,
                new BigDecimal("5000.00"),
                new BigDecimal("50.00"),
                new BigDecimal("1000.00"),
                fixedNow.minus(5, ChronoUnit.DAYS)
        );
        when(balanceUseCase.getBalance(walletId)).thenReturn(new BigDecimal("500.00"));
        when(cashflowProfileUseCase.getCashflowProfileByWalletId(walletId))
                .thenReturn(Optional.of(existing));

        when(cashflowProfileUseCase.saveCashflowProfile(any()))
                .thenAnswer(inv -> {
                    SaveCashflowProfileCommand cmd = inv.getArgument(0);
                    return new CashflowProfile(
                            existing.id(),
                            cmd.userId(),
                            cmd.walletId(),
                            cmd.monthlyIncome(),
                            cmd.monthlyCommittedExpenses(),
                            cmd.minimumSafetyBuffer(),
                            fixedNow
                    );
                });

        CashflowSyncResponse response = service.syncGoals(tenantId, walletId);

        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.monthlyIncome()).isEqualByComparingTo("5000.00");
        assertThat(response.minimumSafetyBuffer()).isEqualByComparingTo("1000.00");
        assertThat(response.monthlyCommittedExpenses()).isEqualByComparingTo("100.00");
        assertThat(response.synced()).isTrue();

        ArgumentCaptor<SaveCashflowProfileCommand> captor = ArgumentCaptor.forClass(SaveCashflowProfileCommand.class);
        verify(cashflowProfileUseCase).saveCashflowProfile(captor.capture());
        SaveCashflowProfileCommand captured = captor.getValue();
        assertThat(captured.monthlyIncome()).isEqualByComparingTo("5000.00");
        assertThat(captured.minimumSafetyBuffer()).isEqualByComparingTo("1000.00");
        assertThat(captured.monthlyCommittedExpenses()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("syncGoals initializes new profile with 0 income and 0 buffer resolving userId via existing AccountUseCase (I-CASH-004)")
    void syncGoalsInitializesNewProfile() {
        when(subscriptionDao.findByWalletId(tenantId, walletId, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(balanceUseCase.getBalance(walletId)).thenReturn(new BigDecimal("200.00"));
        when(cashflowProfileUseCase.getCashflowProfileByWalletId(walletId))
                .thenReturn(Optional.empty());

        Account account = new Account(
                walletId,
                new BigDecimal("200.00"),
                1L,
                userId,
                AccountStatus.ACTIVE,
                null,
                null,
                fixedNow.minus(30, ChronoUnit.DAYS),
                tenantId
        );
        when(accountUseCase.find(walletId)).thenReturn(account);

        when(cashflowProfileUseCase.saveCashflowProfile(any()))
                .thenAnswer(inv -> {
                    SaveCashflowProfileCommand cmd = inv.getArgument(0);
                    return new CashflowProfile(
                            UUID.randomUUID(),
                            cmd.userId(),
                            cmd.walletId(),
                            cmd.monthlyIncome(),
                            cmd.monthlyCommittedExpenses(),
                            cmd.minimumSafetyBuffer(),
                            fixedNow
                    );
                });

        CashflowSyncResponse response = service.syncGoals(tenantId, walletId);

        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.monthlyIncome()).isEqualByComparingTo("0.00");
        assertThat(response.minimumSafetyBuffer()).isEqualByComparingTo("0.00");
        assertThat(response.monthlyCommittedExpenses()).isEqualByComparingTo("0.00");
        assertThat(response.synced()).isTrue();
    }
}
