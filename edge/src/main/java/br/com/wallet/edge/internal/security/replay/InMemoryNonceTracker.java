package br.com.wallet.edge.internal.security.replay;

import br.com.wallet.security.replay.NonceReservation;
import br.com.wallet.security.replay.NonceTracker;
import br.com.wallet.security.replay.ReplayKey;
import br.com.wallet.security.replay.ReplayRejectionReason;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;

/**
 * In-memory fallback NonceTracker using Caffeine with bounded expiration (REQ-SEC-028, I-ENV-004).
 * Used when distributed DragonflyDB/Redis is unavailable or in standalone/test environments.
 */
public final class InMemoryNonceTracker implements NonceTracker {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    private static final long DEFAULT_MAX_SIZE = 50_000L;

    private final Cache<String, String> cache;

    public InMemoryNonceTracker() {
        this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
    }

    public InMemoryNonceTracker(Duration ttl, long maxSize) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Objects.requireNonNull(ttl, "ttl must not be null"))
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public NonceReservation reserve(ReplayKey key) {
        Objects.requireNonNull(key, "key must not be null");
        String storageKey = key.toStorageKey();
        String existing = cache.asMap().putIfAbsent(storageKey, "RESERVED");
        if (existing == null) {
            return new NonceReservation.Admitted();
        }
        return new NonceReservation.Rejected(ReplayRejectionReason.DUPLICATE_NONCE);
    }

    @Override
    public void commit(ReplayKey key) {
        Objects.requireNonNull(key, "key must not be null");
        cache.put(key.toStorageKey(), "COMMITTED");
    }

    @Override
    public void release(ReplayKey key) {
        Objects.requireNonNull(key, "key must not be null");
        cache.invalidate(key.toStorageKey());
    }
}
