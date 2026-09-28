package br.com.wallet.core.context;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.core.tracing.TraceContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record FraudContext(
    @NonNull UUID userId,
    @Nullable UUID targetUserId,
    @NonNull UUID operationId,
    long amountInCents,
    @NonNull Instant timestamp,
    @NonNull String tenantId
) implements TraceContext {

    public FraudContext {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for FraudContext");
        }
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
