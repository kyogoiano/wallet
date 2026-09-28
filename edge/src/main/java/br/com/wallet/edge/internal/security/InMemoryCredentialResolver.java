package br.com.wallet.edge.internal.security;

import br.com.wallet.edge.api.CredentialMaterial;
import br.com.wallet.edge.api.CredentialMetadata;
import br.com.wallet.edge.api.CredentialResolver;
import br.com.wallet.edge.api.CredentialSnapshot;
import br.com.wallet.edge.api.ResolvedCredential;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory thread-safe implementation of CredentialResolver (I-SEC-003, REQ-SEC-004, REQ-SEC-010).
 * Resolves credentials in O(1) time without database queries and supports atomic snapshot updates
 * for zero-downtime key rotation.
 */
public class InMemoryCredentialResolver implements CredentialResolver {

    public static final String DEFAULT_DEV_KEY_ID = "wallet-key-dev-1";
    public static final String DEFAULT_DEV_SECRET = "wallet-secret-dev-key-32-bytes!!";
    public static final String DEFAULT_DEV_TENANT = "tenant-alpha";
    public static final String DEFAULT_DEV_PRINCIPAL = "default-principal";

    private final AtomicReference<CredentialSnapshot> snapshotRef;

    public InMemoryCredentialResolver() {
        this(createDefaultDevSnapshot());
    }

    public InMemoryCredentialResolver(@NonNull CredentialSnapshot initialSnapshot) {
        Objects.requireNonNull(initialSnapshot, "initialSnapshot must not be null");
        this.snapshotRef = new AtomicReference<>(initialSnapshot);
    }

    @Override
    public Optional<ResolvedCredential> resolve(@Nullable String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        return snapshotRef.get().findByKeyId(keyId.trim());
    }

    public void updateSnapshot(@NonNull CredentialSnapshot newSnapshot) {
        Objects.requireNonNull(newSnapshot, "newSnapshot must not be null");
        this.snapshotRef.set(newSnapshot);
    }

    public boolean isRetiring(@Nullable String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return false;
        }
        return snapshotRef.get().isRetiring(keyId.trim());
    }

    public CredentialSnapshot getSnapshot() {
        return snapshotRef.get();
    }

    private static CredentialSnapshot createDefaultDevSnapshot() {
        CredentialMetadata defaultMeta = new CredentialMetadata(
                DEFAULT_DEV_KEY_ID,
                DEFAULT_DEV_TENANT,
                DEFAULT_DEV_PRINCIPAL,
                Set.of("wallet:read", "wallet:write"),
                true
        );
        CredentialMaterial defaultMaterial = new CredentialMaterial(
                DEFAULT_DEV_SECRET.getBytes(StandardCharsets.UTF_8)
        );
        ResolvedCredential defaultCredential = new ResolvedCredential(defaultMeta, defaultMaterial);
        return new CredentialSnapshot(Map.of(DEFAULT_DEV_KEY_ID, defaultCredential), Map.of());
    }
}
