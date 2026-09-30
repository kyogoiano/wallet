package br.com.wallet.edge.internal.security.keymanagement;

import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.KeyContext;
import br.com.wallet.security.keymanagement.KeyManagementClient;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded high-performance plaintext DEK cache adapter for edge ingress (REQ-SEC-026, I-ENV-005, I-ENV-006).
 *
 * <p>Enforces a 10-minute write-expiration TTL, a hard cap of 100,000 encryptions per DEK
 * before forced eviction and rotation, and immediate zeroization of plaintext key material upon eviction.
 */
public final class CachedKeyManagementClient implements KeyManagementClient {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    public static final long DEFAULT_MAX_USES_PER_DEK = 100_000L;

    public record DekCacheKey(TenantId tenantId, KeyId keyId) {
        public DekCacheKey {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(keyId, "keyId must not be null");
        }
    }

    public record DekCacheEntry(
            TenantId tenantId,
            KeyId keyId,
            CryptoBytes wrappedDek,
            SensitiveKeyMaterial plaintextDek,
            Instant createdAt,
            Instant expiresAt,
            AtomicLong encryptionCount
    ) {}

    private final KeyManagementClient delegate;
    private final Duration ttl;
    private final long maxUsesPerDek;
    private final Cache<DekCacheKey, DekCacheEntry> cache;

    public CachedKeyManagementClient(KeyManagementClient delegate) {
        this(delegate, DEFAULT_TTL, DEFAULT_MAX_USES_PER_DEK);
    }

    public CachedKeyManagementClient(KeyManagementClient delegate, Duration ttl, long maxUsesPerDek) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.ttl = ttl != null ? ttl : DEFAULT_TTL;
        this.maxUsesPerDek = maxUsesPerDek > 0 ? maxUsesPerDek : DEFAULT_MAX_USES_PER_DEK;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(this.ttl)
                .maximumSize(1000)
                .removalListener((DekCacheKey key, DekCacheEntry entry, RemovalCause cause) -> {
                    if (entry != null && entry.plaintextDek() != null) {
                        entry.plaintextDek().close();
                    }
                })
                .build();
    }

    @Override
    public GeneratedDataKey generateDataKey(TenantId tenantId, KeyId keyId, KeyContext context) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        DekCacheKey cacheKey = new DekCacheKey(tenantId, keyId);

        DekCacheEntry entry = cache.getIfPresent(cacheKey);
        if (entry == null || entry.encryptionCount().get() >= maxUsesPerDek || entry.plaintextDek().isDestroyed()) {
            if (entry != null) {
                cache.invalidate(cacheKey);
            }
            GeneratedDataKey fresh = delegate.generateDataKey(tenantId, keyId, context);
            entry = new DekCacheEntry(
                    tenantId,
                    keyId,
                    fresh.wrappedDek(),
                    fresh.plaintextDek(),
                    Instant.now(),
                    Instant.now().plus(ttl),
                    new AtomicLong(0)
            );
            cache.put(cacheKey, entry);
        }

        long count = entry.encryptionCount().incrementAndGet();
        if (count >= maxUsesPerDek) {
            cache.invalidate(cacheKey);
        }

        // Return a fresh copy of SensitiveKeyMaterial so caller's auto-close does not invalidate cached master
        return new GeneratedDataKey(
                new SensitiveKeyMaterial(entry.plaintextDek().getEncoded()),
                entry.wrappedDek()
        );
    }

    @Override
    public SensitiveKeyMaterial decryptDataKey(TenantId tenantId, KeyId keyId, CryptoBytes wrappedDek, KeyContext context) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        Objects.requireNonNull(context, "context must not be null");

        DekCacheKey cacheKey = new DekCacheKey(tenantId, keyId);
        DekCacheEntry entry = cache.getIfPresent(cacheKey);

        if (entry != null && !entry.plaintextDek().isDestroyed() && entry.wrappedDek().equals(wrappedDek)) {
            return new SensitiveKeyMaterial(entry.plaintextDek().getEncoded());
        }

        return delegate.decryptDataKey(tenantId, keyId, wrappedDek, context);
    }

    public void invalidateAll() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    public long getActiveCachedEntriesCount() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    DekCacheEntry getEntryForTesting(TenantId tenantId, KeyId keyId) {
        return cache.getIfPresent(new DekCacheKey(tenantId, keyId));
    }
}
