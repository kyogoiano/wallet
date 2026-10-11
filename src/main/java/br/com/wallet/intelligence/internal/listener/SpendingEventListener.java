package br.com.wallet.intelligence.internal.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.intelligence.internal.engine.RecurrencePatternEngine;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring Modulith in-process listener for Spending & Subscription Intelligence.
 * Pure ingress adapter (TASK-3.1.10):
 * - Qualifies outflows (USER origin only, I-SUB-011).
 * - Enforces durable eventId idempotency via SubscriptionDao (I-SUB-008).
 * - Clusters series by (tenantId, walletId, counterpartyId) (REQ-SUB-002, I-SUB-010).
 * - Delegates mathematical modeling to RecurrencePatternEngine (I-SUB-001..003).
 * - Persists projections via SubscriptionDao and publishes spike events (REQ-SUB-006).
 */
@Component
public class SpendingEventListener {

    private static final Logger log = LoggerFactory.getLogger(SpendingEventListener.class);

    private final SubscriptionDao subscriptionDao;
    private final RecurrencePatternEngine recurrencePatternEngine;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicInteger processedEventCount = new AtomicInteger(0);
    private volatile String lastProcessedTenantId;

    public SpendingEventListener(
            @NonNull final SubscriptionDao subscriptionDao,
            @NonNull final RecurrencePatternEngine recurrencePatternEngine,
            @NonNull final ApplicationEventPublisher eventPublisher
    ) {
        this.subscriptionDao = Objects.requireNonNull(subscriptionDao, "subscriptionDao cannot be null");
        this.recurrencePatternEngine = Objects.requireNonNull(recurrencePatternEngine, "recurrencePatternEngine cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
    }

    @ApplicationModuleListener
    public void onTransfer(@NonNull final TransferCompletedEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        validateTenant(event.tenantId());

        // I-SUB-011: Outflow observation contract - only USER origin participates in subscription clustering
        if (event.origin() != OperationOrigin.USER) {
            log.debug("Discarding non-USER TransferCompletedEvent from subscription clustering: eventId={}, origin={}",
                    event.aggregateId(), event.origin());
            return;
        }

        // I-SUB-008: Durable idempotency check
        if (!subscriptionDao.tryRecordProcessedEvent(event.aggregateId(), event.tenantId())) {
            log.info("Duplicate TransferCompletedEvent ignored by SpendingEventListener: eventId={}", event.aggregateId());
            return;
        }

        // REQ-SUB-002: Series clustering (T, W, C) where W = from, C = to
        UUID walletId = event.from();
        UUID counterpartyId = event.to();
        var existing = subscriptionDao.findBySeries(event.tenantId(), walletId, counterpartyId);

        // Delegate to RecurrencePatternEngine
        var result = recurrencePatternEngine.processObservation(
                existing,
                event.tenantId(),
                walletId,
                counterpartyId,
                event.amount(),
                Instant.now(),
                event.aggregateId()
        );

        subscriptionDao.upsert(result.subscription());

        result.spikeEvent().ifPresent(spikeEvent -> {
            log.warn("Price spike detected on subscription {}: baseline={}, new={}",
                    spikeEvent.subscriptionId(), spikeEvent.baselineAmount(), spikeEvent.observedAmount());
            eventPublisher.publishEvent(spikeEvent);
        });

        this.lastProcessedTenantId = event.tenantId();
        this.processedEventCount.incrementAndGet();
    }

    @ApplicationModuleListener
    public void onWithdraw(@NonNull final WithdrawCompletedEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        validateTenant(event.tenantId());

        // I-SUB-008: Durable idempotency check
        if (!subscriptionDao.tryRecordProcessedEvent(event.aggregateId(), event.tenantId())) {
            log.info("Duplicate WithdrawCompletedEvent ignored by SpendingEventListener: eventId={}", event.aggregateId());
            return;
        }

        // I-SUB-011: WithdrawCompletedEvent has no canonical counterparty; discard from subscription clustering
        log.debug("WithdrawCompletedEvent does not have canonical counterparty; skipping subscription clustering: eventId={}",
                event.aggregateId());

        this.lastProcessedTenantId = event.tenantId();
        this.processedEventCount.incrementAndGet();
    }

    private void validateTenant(final String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for spending intelligence event processing");
        }
    }

    public int getProcessedEventCount() {
        return processedEventCount.get();
    }

    public String getLastProcessedTenantId() {
        return lastProcessedTenantId;
    }
}
