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
        @NonNull Instant createdAt,
        @NonNull String tenantId
) {
    public Account {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(balance, "balance cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (tenantId == null) {
            tenantId = "default";
        }
    }

    public Account(
            UUID id,
            BigDecimal balance,
            Long version,
            UUID userId,
            AccountStatus status,
            Instant blockedAt,
            String blockedReason,
            Instant createdAt
    ) {
        this(id, balance, version, userId, status, blockedAt, blockedReason, createdAt, "default");
    }

    public Account(UUID id, BigDecimal balance, Long version, UUID userId, Instant createdAt) {
        this(id, balance, version, userId, AccountStatus.ACTIVE, null, null, createdAt, "default");
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
