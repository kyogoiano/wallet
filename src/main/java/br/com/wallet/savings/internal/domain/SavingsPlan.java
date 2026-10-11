package br.com.wallet.savings.internal.domain;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SavingsPlan(
        @NonNull UUID id,
        @NonNull UUID sourceWalletId,
        @NonNull UUID targetWalletId,
        @NonNull BigDecimal minimumRetainedBalance,
        @NonNull String status,
        @NonNull List<SavingsRule> rules,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {
    public SavingsPlan {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(sourceWalletId, "sourceWalletId cannot be null");
        Objects.requireNonNull(targetWalletId, "targetWalletId cannot be null");
        Objects.requireNonNull(minimumRetainedBalance, "minimumRetainedBalance cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        rules = List.copyOf(rules);
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
