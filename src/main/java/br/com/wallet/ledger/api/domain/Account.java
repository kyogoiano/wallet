package br.com.wallet.ledger.api.domain;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Account(
        @NonNull UUID id,
        @NonNull BigDecimal balance,
        @NonNull Long version,
        @NonNull UUID userId,
        @NonNull AccountStatus status,
        @Nullable Instant blockedAt,
        @Nullable String blockedReason,
        @NonNull Instant createdAt
) {
    public Account {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(balance, "balance cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public Account(UUID id, BigDecimal balance, Long version, UUID userId, Instant createdAt) {
        this(id, balance, version, userId, AccountStatus.ACTIVE, null, null, createdAt);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
