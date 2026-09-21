package br.com.wallet.core.context;

import br.com.wallet.core.tracing.TraceContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FraudContext(
    @NonNull UUID userId,
    @Nullable UUID targetUserId,
    @NonNull UUID operationId,
    long amountInCents,
    @NonNull Instant timestamp,
    @Nullable String tenantId
) implements TraceContext {

    public FraudContext {
        if (tenantId == null) {
            tenantId = "default";
        }
    }

    public FraudContext(@NonNull UUID userId, @Nullable UUID targetUserId, @NonNull UUID operationId, long amountInCents, @NonNull Instant timestamp) {
        this(userId, targetUserId, operationId, amountInCents, timestamp, "default");
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
                "user.id", userId.toString(),
                "timestamp", timestamp.toString()
        );
    }

    public static long toCents(@NonNull BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValueExact();
    }
}
