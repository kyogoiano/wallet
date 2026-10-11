package br.com.wallet.edge.api;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Resolved pair of credential metadata and cryptographic secret material (TASK-SEC-2.2).
 */
public record ResolvedCredential(
        @NonNull CredentialMetadata metadata,
        @NonNull CredentialMaterial material
) {
    public ResolvedCredential {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(material, "material must not be null");
    }
}
