package br.com.wallet.edge.api;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Structured compound key partitioning perimeter token bucket rate limiting (I-SEC-007, REQ-SEC-005).
 */
public record RateLimitKey(
        @NonNull String tenantId,
        @NonNull String principalId
) {
    public RateLimitKey {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(principalId, "principalId must not be null");
    }
}
