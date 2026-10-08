package br.com.wallet.intelligence.internal.engine;

import br.com.wallet.intelligence.api.model.CashflowStatus;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pure domain record holding the evaluated cashflow projection results (I-CASH-001, I-CASH-002, I-CASH-003).
 */
public record CashflowForecast(
        @NonNull BigDecimal liabilities7Days,
        @NonNull BigDecimal liabilities14Days,
        @NonNull BigDecimal liabilities30Days,
        @NonNull BigDecimal shortfall14Days,
        @NonNull BigDecimal shortfall30Days,
        @NonNull CashflowStatus status30Days,
        @NonNull BigDecimal normalizedMonthlyCommitted,
        int activeInstallmentsCount
) {
    public CashflowForecast {
        Objects.requireNonNull(liabilities7Days, "liabilities7Days cannot be null");
        Objects.requireNonNull(liabilities14Days, "liabilities14Days cannot be null");
        Objects.requireNonNull(liabilities30Days, "liabilities30Days cannot be null");
        Objects.requireNonNull(shortfall14Days, "shortfall14Days cannot be null");
        Objects.requireNonNull(shortfall30Days, "shortfall30Days cannot be null");
        Objects.requireNonNull(status30Days, "status30Days cannot be null");
        Objects.requireNonNull(normalizedMonthlyCommitted, "normalizedMonthlyCommitted cannot be null");
    }
}
