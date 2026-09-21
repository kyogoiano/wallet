package br.com.wallet.ledger.api.domain;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record AccountBalance(
        @NonNull UUID userId,
        @NonNull BigDecimal balance,
        @NonNull AccountStatus status,
        @NonNull String tenantId
) {
    public AccountBalance {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(balance, "balance cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (tenantId == null) {
            tenantId = "default";
        }
    }

    public AccountBalance(UUID userId, BigDecimal balance, AccountStatus status) {
        this(userId, balance, status, "default");
    }

    public AccountBalance(UUID userId, BigDecimal balance) {
        this(userId, balance, AccountStatus.ACTIVE, "default");
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
