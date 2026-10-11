package br.com.wallet.intelligence.api.dto;

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

public record SubscriptionResponse(
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
        @NonNull Instant lastObservedAt
) {
    public SubscriptionResponse {
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
    }

    public static SubscriptionResponse from(
            @NonNull final UUID id,
            @NonNull final String tenantId,
            @NonNull final UUID walletId,
            @NonNull final UUID counterpartyId,
            @NonNull final Cadence cadence,
            @NonNull final SubscriptionStatus status,
            @NonNull final PriceState priceState,
            @NonNull final String classification,
            @NonNull final BigDecimal averageAmount,
            @NonNull final BigDecimal lastAmount,
            @NonNull final BigDecimal confidence,
            final int observedCycles,
            @NonNull final VarianceType varianceType,
            @Nullable final Instant nextExpectedAt,
            @NonNull final Instant lastObservedAt
    ) {
        return new SubscriptionResponse(
                id, tenantId, walletId, counterpartyId, cadence, status, priceState,
                classification, averageAmount, lastAmount, confidence, observedCycles,
                varianceType, nextExpectedAt, lastObservedAt
        );
    }
}
