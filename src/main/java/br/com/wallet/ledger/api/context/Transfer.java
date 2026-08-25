package br.com.wallet.ledger.api.context;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.ledger.api.domain.FraudCheckable;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Transfer(
        @NonNull UUID from,
        @NonNull UUID to,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId,
        @NonNull OperationOrigin origin
) implements TraceContext, FraudCheckable {

    public Transfer {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
    }

    public Transfer(@NonNull UUID from, @NonNull UUID to, @NonNull BigDecimal amount, @NonNull UUID operationId) {
        this(from, to, amount, operationId, OperationOrigin.USER);
    }

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
                "wallet.to", to.toString(),
                "operation.origin", origin.name()
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
