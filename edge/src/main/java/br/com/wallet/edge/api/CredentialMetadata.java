package br.com.wallet.edge.api;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable metadata associated with an API client credential (REQ-SEC-004, TASK-SEC-2.1).
 */
public record CredentialMetadata(
        @NonNull String keyId,
        @NonNull String tenantId,
        @NonNull String principalId,
        @NonNull Set<String> permissions,
        boolean active
) {
    public CredentialMetadata {
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(principalId, "principalId must not be null");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
