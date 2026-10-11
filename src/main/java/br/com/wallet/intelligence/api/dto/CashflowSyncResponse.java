package br.com.wallet.intelligence.api.dto;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Cashflow profile sync confirmation DTO (REQ-CASH-005, I-CASH-004).
 */
public record CashflowSyncResponse(
        @NonNull UUID walletId,
        @NonNull UUID userId,
        @NonNull BigDecimal monthlyCommittedExpenses,
        @NonNull BigDecimal monthlyIncome,
        @NonNull BigDecimal minimumSafetyBuffer,
        boolean synced
) {
    public CashflowSyncResponse {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(monthlyCommittedExpenses, "monthlyCommittedExpenses cannot be null");
        Objects.requireNonNull(monthlyIncome, "monthlyIncome cannot be null");
        Objects.requireNonNull(minimumSafetyBuffer, "minimumSafetyBuffer cannot be null");
    }
}
