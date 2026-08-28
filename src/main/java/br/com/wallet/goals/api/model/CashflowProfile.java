package br.com.wallet.goals.api.model;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CashflowProfile(
        @NonNull UUID id,
        @NonNull UUID userId,
        @NonNull UUID walletId,
        @NonNull BigDecimal monthlyIncome,
        @NonNull BigDecimal monthlyCommittedExpenses,
        @NonNull BigDecimal minimumSafetyBuffer,
        @NonNull Instant updatedAt
) {
    public CashflowProfile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(monthlyIncome, "monthlyIncome cannot be null");
        Objects.requireNonNull(monthlyCommittedExpenses, "monthlyCommittedExpenses cannot be null");
        Objects.requireNonNull(minimumSafetyBuffer, "minimumSafetyBuffer cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

        if (monthlyIncome.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("monthlyIncome cannot be negative");
        }
        if (monthlyCommittedExpenses.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("monthlyCommittedExpenses cannot be negative");
        }
        if (minimumSafetyBuffer.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("minimumSafetyBuffer cannot be negative");
        }
    }
}
