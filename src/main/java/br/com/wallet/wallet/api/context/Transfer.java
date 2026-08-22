package br.com.wallet.wallet.api.context;

import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.wallet.api.domain.FraudCheckable;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record Transfer(
        @NonNull UUID from,
        @NonNull UUID to,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId
) implements TraceContext, FraudCheckable {
    @Override
    public UUID operationId() {
        return this.operationId;
    }

    @Override
    public UUID userId() {
        return this.from;
    }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "wallet.from", from.toString(),
                "wallet.to", to.toString()
        );
    }

    @Override
    public UUID getSourceUserIdForFraudCheck() {
        return this.from;
    }

    @Override
    public UUID getTargetUserIdForFraudCheck() {
        return this.to;
    }
}
