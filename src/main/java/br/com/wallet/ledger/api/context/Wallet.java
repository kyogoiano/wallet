package br.com.wallet.ledger.api.context;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.ledger.api.domain.FraudCheckable;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Wallet(
        @NonNull UUID id,
        BigDecimal initialBalance,
        @NonNull UUID userId,
        UUID operationId,
        @NonNull String tenantId
) implements TraceContext, FraudCheckable {

    public Wallet {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        if (tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for Wallet");
        }
    }

    @Override
    public UUID operationId() {
        return this.operationId;
    }

    @Override
    public BigDecimal amount() {
        return this.initialBalance;
    }

    @Override
    public UUID sourceUserIdForFraudCheck() {
        return this.userId;
    }

    @Override
    public UUID targetUserIdForFraudCheck() {
        return null;
    }

    @Override
    public String tenantId() {
        return this.tenantId;
    }

    @Override
    public UUID userId() { return this.userId; }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "user.id", userId.toString(),
                "wallet.id", id.toString(),
                "tenant.id", tenantId
        );
    }
}
