package br.com.wallet.intelligence.internal.rest;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.intelligence.api.dto.SubscriptionResponse;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.engine.RecurrencePatternEngine;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST ingress controller for subscription queries (REQ-SUB-008, I-SUB-010).
 * Scoped strictly to the authenticated tenant context; client-controlled tenant selectors are forbidden.
 */
@RestController
@RequestMapping("/api/v1/intelligence/subscriptions")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionDao subscriptionDao;
    private final RecurrencePatternEngine recurrencePatternEngine;

    public SubscriptionController(@NonNull final SubscriptionDao subscriptionDao) {
        this(subscriptionDao, new RecurrencePatternEngine());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SubscriptionController(
            @NonNull final SubscriptionDao subscriptionDao,
            @NonNull final RecurrencePatternEngine recurrencePatternEngine
    ) {
        this.subscriptionDao = Objects.requireNonNull(subscriptionDao, "subscriptionDao cannot be null");
        this.recurrencePatternEngine = Objects.requireNonNull(recurrencePatternEngine, "recurrencePatternEngine cannot be null");
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptions(
            @PathVariable @NonNull final UUID walletId,
            @RequestParam(required = false) @Nullable final SubscriptionStatus status,
            @NonNull final HttpServletRequest request
    ) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        String tenantId = resolveTenantId(request);
        log.debug("Fetching subscriptions for tenant={}, walletId={}, status={}", tenantId, walletId, status);

        List<Subscription> subscriptions = subscriptionDao.findByWalletId(tenantId, walletId, status);
        java.time.Instant now = java.time.Instant.now();

        List<Subscription> updatedSubscriptions = subscriptions.stream()
                .map(sub -> {
                    if (sub.status() == SubscriptionStatus.ACTIVE
                            && sub.cadence() != br.com.wallet.intelligence.api.model.Cadence.IRREGULAR
                            && sub.observedCycles() >= 2) {
                        double totalDays = (double) java.time.Duration.between(sub.createdAt(), sub.lastObservedAt()).toSeconds() / 86400.0;
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
                .toList();

        List<SubscriptionResponse> responses = updatedSubscriptions.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    private String resolveTenantId(final HttpServletRequest request) {
        Object principalAttr = request.getAttribute("wallet.authenticated_principal");
        if (principalAttr != null) {
            if (principalAttr instanceof String s && !s.isBlank()) {
                return s.trim();
            }
            try {
                var method = principalAttr.getClass().getMethod("tenantId");
                Object val = method.invoke(principalAttr);
                if (val != null && !val.toString().isBlank()) {
                    return val.toString().trim();
                }
            } catch (Exception ignored) {
            }
        }

        Object tenantAttr = request.getAttribute("wallet.tenant_id");
        if (tenantAttr instanceof String s && !s.isBlank()) {
            return s.trim();
        }

        String headerTenant = request.getHeader("X-Tenant-Id");
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant.trim();
        }

        throw new TenantContextMissingException("Authenticated tenant context is required for subscription queries");
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
