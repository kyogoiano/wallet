package br.com.wallet.ledger.api.context;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.ledger.api.domain.FraudCheckable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Withdraw(
        @NonNull UUID walletId,
        @Nullable UUID userId,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId,
        OperationOrigin origin,
        @NonNull String tenantId
) implements TraceContext, FraudCheckable {

    public Withdraw {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        if (origin == null) {
            origin = OperationOrigin.USER;
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for Withdraw");
        }
    }

    public Withdraw(@NonNull UUID walletId, @Nullable UUID userId, @NonNull BigDecimal amount, @NonNull UUID operationId, @NonNull String tenantId) {
        this(walletId, userId, amount, operationId, OperationOrigin.USER, tenantId);
    }

    @Override
    public UUID operationId() {
        return this.operationId;
    }

    @Override
    public UUID userId() {
        return this.userId;
    }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "wallet.id", walletId.toString(),
                "operation.origin", origin.name(),
                "tenant.id", tenantId
        );
    }

    @Override
    public UUID sourceUserIdForFraudCheck() {
        return this.userId;
    }

    @Override
    public UUID targetUserIdForFraudCheck() {
        return null; // Withdrawals don't have a target user
    }
}