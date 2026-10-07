package br.com.wallet.intelligence.internal.listener;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring Modulith in-process listener for Spending & Subscription Intelligence (Phase 3.0 Module Foundation).
 * Observes financial events in-process without NATS dependencies (I-INTEL-002, I-STREAM-001)
 * and without mutating ledger or account balances (I-INTEL-001).
 * Enforces strict multi-tenant isolation (I-INTEL-010) and eventId idempotency (I-INTEL-008).
 */
@Component
public class SpendingEventListener {

    private static final Logger log = LoggerFactory.getLogger(SpendingEventListener.class);

    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger processedEventCount = new AtomicInteger(0);
    private volatile String lastProcessedTenantId;

    public SpendingEventListener() {
    }

    @ApplicationModuleListener
    public void onTransfer(@NonNull final TransferCompletedEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        validateTenant(event.tenantId());

        if (!processedEventIds.add(event.aggregateId())) {
            log.info("Duplicate TransferCompletedEvent ignored by SpendingEventListener: eventId={}", event.aggregateId());
            return;
        }

        log.info("Processing TransferCompletedEvent in SpendingEventListener: eventId={}, tenantId={}, amount={}",
                event.aggregateId(), event.tenantId(), event.amount());
        this.lastProcessedTenantId = event.tenantId();
        this.processedEventCount.incrementAndGet();
    }

    @ApplicationModuleListener
    public void onWithdraw(@NonNull final WithdrawCompletedEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        validateTenant(event.tenantId());

        if (!processedEventIds.add(event.aggregateId())) {
            log.info("Duplicate WithdrawCompletedEvent ignored by SpendingEventListener: eventId={}", event.aggregateId());
            return;
        }

        log.info("Processing WithdrawCompletedEvent in SpendingEventListener: eventId={}, tenantId={}, amount={}",
                event.aggregateId(), event.tenantId(), event.amount());
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
