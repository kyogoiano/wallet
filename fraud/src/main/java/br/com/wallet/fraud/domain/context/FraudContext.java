package br.com.wallet.fraud.domain.context;

import br.com.wallet.core.tracing.TraceContext;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FraudContext(
    @NonNull String userId,
    @NonNull String targetUserId,
    @NonNull UUID operationId,
    @NonNull BigDecimal amount,
    @NonNull Instant timestamp
) implements TraceContext {
    @Override
    public UUID operationId() {
        return this.operationId;
    }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "user.id", userId,
                "target.user.id", targetUserId,
                "timestamp", timestamp.toString()
        );
    }
}