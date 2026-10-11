package br.com.wallet.edge.api;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Service Provider Interface (SPI) for resolving API credentials at the Edge perimeter
 * in O(1) in-memory time with zero database dependencies (I-SEC-003, TASK-SEC-2.2).
 */
public interface CredentialResolver {

    /**
     * Resolves credentials for the given keyId.
     *
     * @param keyId the client-supplied key identifier
     * @return Optional containing the resolved credential if found and active, otherwise empty
     */
    Optional<ResolvedCredential> resolve(@Nullable String keyId);
}
