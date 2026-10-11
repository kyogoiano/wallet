package br.com.wallet.edge.api;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Set;

/**
 * Represents an authenticated API principal verified at the Edge perimeter (TASK-SEC-2.1).
 */
public record AuthenticatedPrincipal(
        @NonNull String principalId,
        @NonNull String tenantId,
        @NonNull String keyId,
        @NonNull Set<String> permissions
) {
    public AuthenticatedPrincipal {
        Objects.requireNonNull(principalId, "principalId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
