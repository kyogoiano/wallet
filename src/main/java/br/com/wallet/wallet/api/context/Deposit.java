package br.com.wallet.wallet.api.context;

import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.wallet.api.domain.FraudCheckable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record Deposit(@NonNull UUID walletId,
                      @Nullable UUID userId,
                      @NonNull BigDecimal amount,
                      @NonNull UUID operationId) implements TraceContext, FraudCheckable {
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
                "wallet.id", walletId.toString()
        );
    }

    @Override
    public UUID getSourceUserIdForFraudCheck() {
        return this.userId;
    }

    @Override
    public UUID getTargetUserIdForFraudCheck() {
        return null; // Deposits don't have a target user
    }
}
