package br.com.wallet.edge.api;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of credentials supporting overlapping active and retiring
 * keys to ensure zero-downtime credential rotation (REQ-SEC-010, TASK-SEC-2.1).
 */
public record CredentialSnapshot(
        @NonNull Map<String, ResolvedCredential> activeCredentials,
        @NonNull Map<String, ResolvedCredential> retiringCredentials
) {
    public CredentialSnapshot {
        activeCredentials = Map.copyOf(activeCredentials);
        retiringCredentials = Map.copyOf(retiringCredentials);
    }

    /**
     * Attempts to find a credential by keyId in active credentials first, then retiring credentials.
     *
     * @param keyId the credential key identifier
     * @return Optional containing the ResolvedCredential if found and active, else empty
     */
    public Optional<ResolvedCredential> findByKeyId(String keyId) {
        if (keyId == null) {
            return Optional.empty();
        }
        ResolvedCredential credential = activeCredentials.get(keyId);
        if (credential != null && credential.metadata().active()) {
            return Optional.of(credential);
        }
        ResolvedCredential retiring = retiringCredentials.get(keyId);
        if (retiring != null && retiring.metadata().active()) {
            return Optional.of(retiring);
        }
        return Optional.empty();
    }

    /**
     * Checks whether the given key is currently in the retiring grace period.
     *
     * @param keyId the key identifier
     * @return true if the key exists in retiringCredentials
     */
    public boolean isRetiring(String keyId) {
        return keyId != null && retiringCredentials.containsKey(keyId);
    }
}
