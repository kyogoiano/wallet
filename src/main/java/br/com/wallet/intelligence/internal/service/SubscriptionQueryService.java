package br.com.wallet.intelligence.internal.service;

import br.com.wallet.intelligence.api.SubscriptionQueryUseCase;
import br.com.wallet.intelligence.api.dto.SubscriptionResponse;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.engine.RecurrencePatternEngine;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service implementation for querying subscriptions with dynamic cancellation inference (REQ-SUB-008, I-SUB-010).
 */
@Service
public class SubscriptionQueryService implements SubscriptionQueryUseCase {

    private final SubscriptionDao subscriptionDao;
    private final RecurrencePatternEngine recurrencePatternEngine;
    private final Clock clock;

    public SubscriptionQueryService(@NonNull final SubscriptionDao subscriptionDao) {
        this(subscriptionDao, new RecurrencePatternEngine(), Clock.systemUTC());
    }

    @Autowired
    public SubscriptionQueryService(
            @NonNull final SubscriptionDao subscriptionDao,
            @NonNull final RecurrencePatternEngine recurrencePatternEngine,
            @NonNull final Clock clock
    ) {
        this.subscriptionDao = Objects.requireNonNull(subscriptionDao, "subscriptionDao cannot be null");
        this.recurrencePatternEngine = Objects.requireNonNull(recurrencePatternEngine, "recurrencePatternEngine cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    @NonNull
    public List<SubscriptionResponse> getSubscriptions(
            @NonNull final String tenantId,
            @NonNull final UUID walletId,
            @Nullable final SubscriptionStatus status
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");

        List<Subscription> subscriptions = subscriptionDao.findByWalletId(tenantId, walletId, status);
        Instant now = clock.instant();

        return subscriptions.stream()
                .map(sub -> {
                    if (sub.status() == SubscriptionStatus.ACTIVE
                            && sub.cadence() != Cadence.IRREGULAR
                            && sub.observedCycles() >= 2) {
                        double totalDays = (double) Duration.between(sub.createdAt(), sub.lastObservedAt()).toSeconds() / 86400.0;
                        double avgInterval = totalDays / (sub.observedCycles() - 1);
                        if (recurrencePatternEngine.shouldInferCancellation(sub.status(), sub.cadence(), avgInterval, sub.lastObservedAt(), now)) {
                            Subscription cancelled = new Subscription(
                                    sub.id(), sub.tenantId(), sub.walletId(), sub.counterpartyId(),
                                    sub.cadence(), SubscriptionStatus.CANCELLED_INFERRED, sub.priceState(),
                                    sub.classification(), sub.averageAmount(), sub.lastAmount(),
                                    sub.confidence(), sub.observedCycles(), sub.varianceType(),
                                    null, sub.lastObservedAt(), sub.createdAt(), now
                            );
                            subscriptionDao.upsert(cancelled);
                            return cancelled;
                        }
                    }
                    return sub;
                })
                .filter(sub -> status == null || sub.status() == status)
                .map(this::toResponse)
                .toList();
    }

    private SubscriptionResponse toResponse(final Subscription sub) {
        return new SubscriptionResponse(
                sub.id(),
                sub.tenantId(),
                sub.walletId(),
                sub.counterpartyId(),
                sub.cadence(),
                sub.status(),
                sub.priceState(),
                sub.classification(),
                sub.averageAmount(),
                sub.lastAmount(),
                sub.confidence(),
                sub.observedCycles(),
                sub.varianceType(),
                sub.nextExpectedAt(),
                sub.lastObservedAt()
        );
    }
}
