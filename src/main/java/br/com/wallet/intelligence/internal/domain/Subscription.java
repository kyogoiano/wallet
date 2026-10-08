package br.com.wallet.intelligence.internal.domain;

import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Subscription(
        @NonNull UUID id,
        @NonNull String tenantId,
        @NonNull UUID walletId,
        @NonNull UUID counterpartyId,
        @NonNull Cadence cadence,
        @NonNull SubscriptionStatus status,
        @NonNull PriceState priceState,
        @NonNull String classification,
        @NonNull BigDecimal averageAmount,
        @NonNull BigDecimal lastAmount,
        @NonNull BigDecimal confidence,
        int observedCycles,
        @NonNull VarianceType varianceType,
        @Nullable Instant nextExpectedAt,
        @NonNull Instant lastObservedAt,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {
    public Subscription {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(counterpartyId, "counterpartyId cannot be null");
        Objects.requireNonNull(cadence, "cadence cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(priceState, "priceState cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");
        Objects.requireNonNull(averageAmount, "averageAmount cannot be null");
        Objects.requireNonNull(lastAmount, "lastAmount cannot be null");
        Objects.requireNonNull(confidence, "confidence cannot be null");
        Objects.requireNonNull(varianceType, "varianceType cannot be null");
        Objects.requireNonNull(lastObservedAt, "lastObservedAt cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
