package br.com.wallet.ledger.api.domain;

import br.com.wallet.core.exceptions.TenantContextMissingException;
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
        if (tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for AccountBalance");
        }
    }

    public AccountBalance(@NonNull UUID userId, @NonNull BigDecimal balance, @NonNull String tenantId) {
        this(userId, balance, AccountStatus.ACTIVE, tenantId);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
