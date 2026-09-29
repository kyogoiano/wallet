package br.com.wallet.edge.internal.security.replay;

import br.com.wallet.security.replay.NonceReservation;
import br.com.wallet.security.replay.NonceTracker;
import br.com.wallet.security.replay.ReplayAvailabilityReason;
import br.com.wallet.security.replay.ReplayKey;
import br.com.wallet.security.replay.ReplayRejectionReason;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * High-performance Dragonfly/Redis adapter for two-phase nonce admission and replay protection (REQ-SEC-028, REQ-SEC-029, I-ENV-004).
 */
public final class DragonflyNonceTracker implements NonceTracker {

    private static final Logger log = LoggerFactory.getLogger(DragonflyNonceTracker.class);
    private static final long DEFAULT_RESERVATION_TTL_SECONDS = 10L;
    private static final long DEFAULT_COMMIT_TTL_SECONDS = 60L;

    private final RedisCommands<String, String> redisCommands;
    private final long reservationTtlSeconds;
    private final long commitTtlSeconds;

    public DragonflyNonceTracker(RedisCommands<String, String> redisCommands) {
        this(redisCommands, DEFAULT_RESERVATION_TTL_SECONDS, DEFAULT_COMMIT_TTL_SECONDS);
    }

    public DragonflyNonceTracker(
            RedisCommands<String, String> redisCommands,
            long reservationTtlSeconds,
            long commitTtlSeconds
    ) {
        this.redisCommands = Objects.requireNonNull(redisCommands, "redisCommands must not be null");
        this.reservationTtlSeconds = reservationTtlSeconds;
        this.commitTtlSeconds = commitTtlSeconds;
    }

    @Override
    public NonceReservation reserve(ReplayKey key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            SetArgs args = SetArgs.Builder.nx().ex(reservationTtlSeconds);
            String result = redisCommands.set(key.toStorageKey(), "RESERVED", args);

            if ("OK".equals(result)) {
                return new NonceReservation.Admitted();
            }
            return new NonceReservation.Rejected(ReplayRejectionReason.DUPLICATE_NONCE);
        } catch (Exception ex) {
            log.warn("Redis failure during nonce reservation for {}: {}", key.toStorageKey(), ex.getMessage());
            return new NonceReservation.Unavailable(ReplayAvailabilityReason.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void commit(ReplayKey key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            SetArgs args = SetArgs.Builder.xx().ex(commitTtlSeconds);
            redisCommands.set(key.toStorageKey(), "COMMITTED", args);
        } catch (Exception ex) {
            log.warn("Redis failure during nonce commit for {}: {}", key.toStorageKey(), ex.getMessage());
        }
    }

    @Override
    public void release(ReplayKey key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            redisCommands.del(key.toStorageKey());
        } catch (Exception ex) {
            log.warn("Redis failure during nonce release for {}: {}", key.toStorageKey(), ex.getMessage());
        }
    }
}
