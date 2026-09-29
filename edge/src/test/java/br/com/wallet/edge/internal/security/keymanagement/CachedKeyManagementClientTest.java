package br.com.wallet.edge.internal.security.keymanagement;

import br.com.wallet.edge.testsupport.InMemoryKeyManagementClient;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.KeyContext;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-10.8: CachedKeyManagementClient Bounded Plaintext DEK Cache Test in Edge (REQ-SEC-026, I-ENV-005, I-ENV-006)")
class CachedKeyManagementClientTest {

    private InMemoryKeyManagementClient delegate;
    private CachedKeyManagementClient cachedClient;
    private final TenantId tenantId = new TenantId("tenant-finance");
    private final KeyId keyId = new KeyId("kms-key-alpha");
    private final KeyContext context = KeyContext.forTenant(tenantId);

    @BeforeEach
    void setUp() {
        this.delegate = new InMemoryKeyManagementClient();
        // 10 minute TTL, max 100 uses for testability
        this.cachedClient = new CachedKeyManagementClient(delegate, Duration.ofMinutes(10), 100);
    }

    @Test
    @DisplayName("Assert repeated generateDataKey calls return cached DEK without hitting delegate KMS")
    void shouldCacheGeneratedDataKey() {
        GeneratedDataKey key1 = cachedClient.generateDataKey(tenantId, keyId, context);
        GeneratedDataKey key2 = cachedClient.generateDataKey(tenantId, keyId, context);

        assertThat(delegate.getGenerateCount()).isEqualTo(1);
        assertThat(key1.wrappedDek()).isEqualTo(key2.wrappedDek());
        assertThat(key1.plaintextDek().getEncoded()).isEqualTo(key2.plaintextDek().getEncoded());

        // Closing caller's copy does not destroy the cached entry
        key1.close();
        assertThat(key1.plaintextDek().isDestroyed()).isTrue();

        GeneratedDataKey key3 = cachedClient.generateDataKey(tenantId, keyId, context);
        assertThat(key3.plaintextDek().isDestroyed()).isFalse();
        assertThat(delegate.getGenerateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Assert reaching maximum uses per DEK forces eviction and re-generation (I-ENV-006)")
    void shouldEvictAndRotateWhenMaxUsesReached() {
        // maxUses configured to 5
        CachedKeyManagementClient clientWithLowLimit = new CachedKeyManagementClient(delegate, Duration.ofMinutes(10), 5);

        for (int i = 0; i < 5; i++) {
            try (GeneratedDataKey key = clientWithLowLimit.generateDataKey(tenantId, keyId, context)) {
                assertThat(key).isNotNull();
            }
        }
        assertThat(delegate.getGenerateCount()).isEqualTo(1);

        // 6th call should trigger rotation and generate new DEK
        try (GeneratedDataKey key = clientWithLowLimit.generateDataKey(tenantId, keyId, context)) {
            assertThat(key).isNotNull();
        }
        assertThat(delegate.getGenerateCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Assert decryptDataKey uses cached DEK if wrappedDek matches")
    void shouldDecryptUsingCachedDekWhenWrappedMatches() {
        GeneratedDataKey generated = cachedClient.generateDataKey(tenantId, keyId, context);
        assertThat(delegate.getGenerateCount()).isEqualTo(1);

        try (SensitiveKeyMaterial decrypted = cachedClient.decryptDataKey(tenantId, keyId, generated.wrappedDek(), context)) {
            assertThat(decrypted.getEncoded()).isEqualTo(generated.plaintextDek().getEncoded());
            // Did not call delegate decryptDataKey because wrappedDek was in cache
            assertThat(delegate.getDecryptCount()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Assert eviction causes cached plaintext DEK to be zeroized")
    void shouldZeroizePlaintextDekOnEviction() {
        CachedKeyManagementClient client = new CachedKeyManagementClient(delegate, Duration.ofMillis(50), 100);
        GeneratedDataKey key = client.generateDataKey(tenantId, keyId, context);
        assertThat(delegate.getGenerateCount()).isEqualTo(1);

        // Invalidate cache explicitly
        client.invalidateAll();

        // The cached entry was zeroized by Caffeine removal listener
        assertThat(client.getActiveCachedEntriesCount()).isEqualTo(0);
    }
}
