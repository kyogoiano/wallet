package br.com.wallet.intelligence.api.event;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event emitted when a forward cashflow shortfall deficit warning is detected (REQ-CASH-006, I-CASH-002).
 */
public record CashflowShortfallAlertEvent(
        @NonNull UUID eventId,
        @NonNull String tenantId,
        @NonNull UUID walletId,
        @NonNull BigDecimal currentBalance,
        @NonNull BigDecimal liabilities14Days,
        @NonNull BigDecimal shortfall14Days,
        @NonNull Instant detectedAt
) {
    public CashflowShortfallAlertEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");
        Objects.requireNonNull(liabilities14Days, "liabilities14Days cannot be null");
        Objects.requireNonNull(shortfall14Days, "shortfall14Days cannot be null");
        Objects.requireNonNull(detectedAt, "detectedAt cannot be null");
    }
}
