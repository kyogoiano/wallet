package br.com.wallet.unit.edge.security;

import br.com.wallet.edge.api.CredentialMaterial;
import br.com.wallet.edge.api.CredentialMetadata;
import br.com.wallet.edge.api.CredentialSnapshot;
import br.com.wallet.edge.api.ResolvedCredential;
import br.com.wallet.edge.internal.security.InMemoryCredentialResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryCredentialResolver Tests (I-SEC-003, REQ-SEC-004, REQ-SEC-010)")
class InMemoryCredentialResolverTest {

    private static final byte[] SECRET_A = "secret-key-alpha-32-bytes-long!!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECRET_B = "secret-key-bravo-32-bytes-long!!".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("Should resolve active credential successfully in O(1)")
    void shouldResolveActiveCredentialSuccessfully() {
        CredentialMetadata metadata = new CredentialMetadata(
                "key-alpha",
                "tenant-100",
                "principal-100",
                Set.of("wallet:write"),
                true
        );
        ResolvedCredential resolved = new ResolvedCredential(metadata, new CredentialMaterial(SECRET_A));
        CredentialSnapshot snapshot = new CredentialSnapshot(Map.of("key-alpha", resolved), Map.of());
        InMemoryCredentialResolver resolver = new InMemoryCredentialResolver(snapshot);

        Optional<ResolvedCredential> result = resolver.resolve("key-alpha");

        assertThat(result).isPresent();
        assertThat(result.get().metadata().tenantId()).isEqualTo("tenant-100");
        assertThat(result.get().metadata().principalId()).isEqualTo("principal-100");
        assertThat(result.get().material().secret()).isEqualTo(SECRET_A);
    }

    @Test
    @DisplayName("Should return empty for unknown keyId")
    void shouldReturnEmptyForUnknownKeyId() {
        InMemoryCredentialResolver resolver = new InMemoryCredentialResolver(
                new CredentialSnapshot(Map.of(), Map.of())
        );

        assertThat(resolver.resolve("unknown-key")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for null or blank keyId")
    void shouldReturnEmptyForNullOrBlankKeyId() {
        InMemoryCredentialResolver resolver = new InMemoryCredentialResolver(
                new CredentialSnapshot(Map.of(), Map.of())
        );

        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("   ")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when credential is inactive")
    void shouldReturnEmptyWhenCredentialIsInactive() {
        CredentialMetadata metadata = new CredentialMetadata(
                "key-inactive",
                "tenant-100",
                "principal-100",
                Set.of("wallet:write"),
                false
        );
        ResolvedCredential resolved = new ResolvedCredential(metadata, new CredentialMaterial(SECRET_A));
        InMemoryCredentialResolver resolver = new InMemoryCredentialResolver(
                new CredentialSnapshot(Map.of("key-inactive", resolved), Map.of())
        );

        assertThat(resolver.resolve("key-inactive")).isEmpty();
    }

    @Test
    @DisplayName("Should resolve retiring credential during key rotation grace period (REQ-SEC-010)")
    void shouldResolveRetiringCredentialDuringRotation() {
        CredentialMetadata metaActive = new CredentialMetadata(
                "key-v2", "tenant-100", "principal-100", Set.of("wallet:write"), true
        );
        CredentialMetadata metaRetiring = new CredentialMetadata(
                "key-v1", "tenant-100", "principal-100", Set.of("wallet:write"), true
        );

        ResolvedCredential credActive = new ResolvedCredential(metaActive, new CredentialMaterial(SECRET_B));
        ResolvedCredential credRetiring = new ResolvedCredential(metaRetiring, new CredentialMaterial(SECRET_A));

        CredentialSnapshot snapshot = new CredentialSnapshot(
                Map.of("key-v2", credActive),
                Map.of("key-v1", credRetiring)
        );
        InMemoryCredentialResolver resolver = new InMemoryCredentialResolver(snapshot);

        assertThat(resolver.resolve("key-v2")).isPresent();
        assertThat(resolver.resolve("key-v1")).isPresent();
        assertThat(resolver.isRetiring("key-v1")).isTrue();
        assertThat(resolver.isRetiring("key-v2")).isFalse();
    }

    @Test
    @DisplayName("Should update credential snapshot atomically")
    void shouldUpdateCredentialSnapshotAtomically() {
        CredentialMetadata meta1 = new CredentialMetadata("key-1", "tenant-1", "p-1", Set.of(), true);
        CredentialMetadata meta2 = new CredentialMetadata("key-2", "tenant-2", "p-2", Set.of(), true);

        InMemoryCredentialResolver resolver = new InMemoryCredentialResolver(
                new CredentialSnapshot(Map.of("key-1", new ResolvedCredential(meta1, new CredentialMaterial(SECRET_A))), Map.of())
        );

        assertThat(resolver.resolve("key-1")).isPresent();
        assertThat(resolver.resolve("key-2")).isEmpty();

        resolver.updateSnapshot(new CredentialSnapshot(
                Map.of("key-2", new ResolvedCredential(meta2, new CredentialMaterial(SECRET_B))), Map.of()
        ));

        assertThat(resolver.resolve("key-1")).isEmpty();
        assertThat(resolver.resolve("key-2")).isPresent();
    }
}
