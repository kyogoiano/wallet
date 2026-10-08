package br.com.wallet.intelligence.api.event;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SubscriptionPriceSpikeEvent(
        @NonNull UUID subscriptionId,
        @NonNull String tenantId,
        @NonNull UUID walletId,
        @NonNull UUID counterpartyId,
        @NonNull BigDecimal baselineAmount,
        @NonNull BigDecimal observedAmount,
        @NonNull BigDecimal percentageIncrease,
        @NonNull UUID triggerEventId,
        @NonNull Instant detectedAt
) {
    public SubscriptionPriceSpikeEvent {
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(counterpartyId, "counterpartyId cannot be null");
        Objects.requireNonNull(baselineAmount, "baselineAmount cannot be null");
        Objects.requireNonNull(observedAmount, "observedAmount cannot be null");
        Objects.requireNonNull(percentageIncrease, "percentageIncrease cannot be null");
        Objects.requireNonNull(triggerEventId, "triggerEventId cannot be null");
        Objects.requireNonNull(detectedAt, "detectedAt cannot be null");
    }
}
