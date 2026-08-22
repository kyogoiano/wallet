package br.com.wallet.ledger.api.context;

import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.ledger.api.domain.FraudCheckable;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record Wallet(@NonNull UUID id,
                     BigDecimal initialBalance,
                     @NonNull UUID userId,
                     UUID operationId) implements TraceContext, FraudCheckable {
    @Override
    public UUID operationId() {
        return this.operationId;
    }

    @Override
    public BigDecimal amount() {
        return this.initialBalance;
    }

    @Override
    public UUID getSourceUserIdForFraudCheck() {
        return this.userId;
    }

    @Override
    public UUID getTargetUserIdForFraudCheck() {
        return null;
    }

    @Override
    public UUID userId() { return this.userId; }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "user.id", userId.toString(),
                "wallet.id", id.toString()
        );
    }
}
