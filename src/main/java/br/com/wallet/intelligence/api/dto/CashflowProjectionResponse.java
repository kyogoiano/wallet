package br.com.wallet.intelligence.api.dto;

import br.com.wallet.intelligence.api.model.CashflowStatus;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Forward cashflow liquidity projection DTO (REQ-CASH-004, I-CASH-001, I-CASH-002).
 */
public record CashflowProjectionResponse(
        @NonNull String tenantId,
        @NonNull UUID walletId,
        @NonNull BigDecimal currentBalance,
        @NonNull BigDecimal liabilities7Days,
        @NonNull BigDecimal liabilities14Days,
        @NonNull BigDecimal liabilities30Days,
        @NonNull BigDecimal shortfall14Days,
        @NonNull BigDecimal shortfall30Days,
        @NonNull CashflowStatus status30Days,
        @NonNull BigDecimal normalizedMonthlyCommitted,
        int activeInstallmentsCount
) {
    public CashflowProjectionResponse {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");
        Objects.requireNonNull(liabilities7Days, "liabilities7Days cannot be null");
        Objects.requireNonNull(liabilities14Days, "liabilities14Days cannot be null");
        Objects.requireNonNull(liabilities30Days, "liabilities30Days cannot be null");
        Objects.requireNonNull(shortfall14Days, "shortfall14Days cannot be null");
        Objects.requireNonNull(shortfall30Days, "shortfall30Days cannot be null");
        Objects.requireNonNull(status30Days, "status30Days cannot be null");
        Objects.requireNonNull(normalizedMonthlyCommitted, "normalizedMonthlyCommitted cannot be null");
    }
}
