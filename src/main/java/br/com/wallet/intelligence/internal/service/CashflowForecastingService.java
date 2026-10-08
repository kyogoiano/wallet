package br.com.wallet.intelligence.internal.service;

import br.com.wallet.goals.api.CashflowProfileUseCase;
import br.com.wallet.goals.api.dto.SaveCashflowProfileCommand;
import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import br.com.wallet.intelligence.api.dto.CashflowSyncResponse;
import br.com.wallet.intelligence.api.event.CashflowShortfallAlertEvent;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.engine.CashflowForecast;
import br.com.wallet.intelligence.internal.engine.CashflowForecastingEngine;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.domain.Account;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service orchestrating forward cashflow projection calculations and Goals CashflowProfile synchronization (REQ-CASH-004, REQ-CASH-005).
 * Guarantees non-locking balance reads (I-CASH-006) and strict preservation of goals safety buffers (I-CASH-004).
 */
@Service
public class CashflowForecastingService {

    private final SubscriptionDao subscriptionDao;
    private final BalanceUseCase balanceUseCase;
    private final AccountUseCase accountUseCase;
    private final CashflowProfileUseCase cashflowProfileUseCase;
    private final CashflowForecastingEngine forecastEngine;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CashflowForecastingService(
            @NonNull final SubscriptionDao subscriptionDao,
            @NonNull final BalanceUseCase balanceUseCase,
            @NonNull final AccountUseCase accountUseCase,
            @NonNull final CashflowProfileUseCase cashflowProfileUseCase,
            @NonNull final CashflowForecastingEngine forecastEngine,
            @NonNull final Clock clock,
            @NonNull final ApplicationEventPublisher eventPublisher
    ) {
        this.subscriptionDao = Objects.requireNonNull(subscriptionDao, "subscriptionDao cannot be null");
        this.balanceUseCase = Objects.requireNonNull(balanceUseCase, "balanceUseCase cannot be null");
        this.accountUseCase = Objects.requireNonNull(accountUseCase, "accountUseCase cannot be null");
        this.cashflowProfileUseCase = Objects.requireNonNull(cashflowProfileUseCase, "cashflowProfileUseCase cannot be null");
        this.forecastEngine = Objects.requireNonNull(forecastEngine, "forecastEngine cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
    }

    @NonNull
    public CashflowProjectionResponse getProjections(
            @NonNull final String tenantId,
            @NonNull final UUID walletId
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");

        BigDecimal balance = balanceUseCase.getBalance(walletId);
        List<Subscription> activeSubscriptions = subscriptionDao.findByWalletId(tenantId, walletId, SubscriptionStatus.ACTIVE);

        CashflowForecast forecast = forecastEngine.forecast(activeSubscriptions, balance, clock.instant());

        return new CashflowProjectionResponse(
                tenantId,
                walletId,
                balance,
                forecast.liabilities7Days(),
                forecast.liabilities14Days(),
                forecast.liabilities30Days(),
                forecast.shortfall14Days(),
                forecast.shortfall30Days(),
                forecast.status30Days(),
                forecast.normalizedMonthlyCommitted(),
                forecast.activeInstallmentsCount()
        );
    }

    @NonNull
    public CashflowSyncResponse syncGoals(
            @NonNull final String tenantId,
            @NonNull final UUID walletId
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");

        List<Subscription> activeSubscriptions = subscriptionDao.findByWalletId(tenantId, walletId, SubscriptionStatus.ACTIVE);
        BigDecimal normalizedCommitted = forecastEngine.calculateMonthlyCommittedExpenses(activeSubscriptions);

        Optional<CashflowProfile> existingOpt = cashflowProfileUseCase.getCashflowProfileByWalletId(walletId);

        UUID userId;
        BigDecimal monthlyIncome;
        BigDecimal minimumSafetyBuffer;

        if (existingOpt.isPresent()) {
            CashflowProfile existing = existingOpt.get();
            userId = existing.userId();
            monthlyIncome = existing.monthlyIncome();
            minimumSafetyBuffer = existing.minimumSafetyBuffer();
        } else {
            Account account = accountUseCase.find(walletId);
            userId = account.userId();
            monthlyIncome = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
            minimumSafetyBuffer = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }

        SaveCashflowProfileCommand command = new SaveCashflowProfileCommand(
                userId,
                walletId,
                monthlyIncome,
                normalizedCommitted,
                minimumSafetyBuffer
        );

        CashflowProfile saved = cashflowProfileUseCase.saveCashflowProfile(command);

        // Check 14-day shortfall alert if applicable (REQ-CASH-006)
        BigDecimal balance = balanceUseCase.getBalance(walletId);
        CashflowForecast forecast = forecastEngine.forecast(activeSubscriptions, balance, clock.instant());
        if (forecast.shortfall14Days().compareTo(BigDecimal.ZERO) > 0) {
            eventPublisher.publishEvent(new CashflowShortfallAlertEvent(
                    UUID.randomUUID(),
                    tenantId,
                    walletId,
                    balance,
                    forecast.liabilities14Days(),
                    forecast.shortfall14Days(),
                    clock.instant()
            ));
        }

        return new CashflowSyncResponse(
                saved.walletId(),
                saved.userId(),
                saved.monthlyCommittedExpenses(),
                saved.monthlyIncome(),
                saved.minimumSafetyBuffer(),
                true
        );
    }
}
