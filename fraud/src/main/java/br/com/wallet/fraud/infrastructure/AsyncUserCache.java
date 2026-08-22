package br.com.wallet.fraud.infrastructure;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public abstract class AsyncUserCache <T> {

    public static final int CACHE_MAXIMUM_SIZE = 10_000;
    public static final Duration REFRESH_DURATION = Duration.ofMinutes(2);
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private AsyncLoadingCache<UUID, T> cache;

    @PostConstruct
    void init() {
        this.cache = fraudCache();
    }

    private AsyncLoadingCache<UUID, T> fraudCache() {

        return Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfter(this.expirySetup())
                .refreshAfterWrite(REFRESH_DURATION)
                .recordStats()
                .buildAsync((userId, executor) ->
                        this.isBlockedAsync(userId).exceptionally(ex -> {
                            log.error("Redis failure", ex);
                            return getFallbackValue(userId);
                        }).toCompletableFuture()
                );
    }

    abstract CompletionStage<T> isBlockedAsync(@NonNull final UUID userId);
    abstract T getFallbackValue(@NonNull UUID userId);
    abstract long expire(@NonNull UUID key, @NonNull T value, long currentTime);

    /**
     * negative caching expiry setup
     * blocked user -> strong cache
     * non-blocked user -> weak cache
     * @return expiry object
     */
    private Expiry<UUID, T> expirySetup() {
        return new Expiry<>() {

            @Override
            public long expireAfterCreate(@NonNull UUID key, @NonNull T value, long currentTime) {
                return expire(key, value, currentTime);
            }

            @Override
            public long expireAfterUpdate(@NonNull UUID key, @NonNull T value, long currentTime, long currentDuration) {
                return expire(key, value, currentTime);
            }

            @Override
            public long expireAfterRead(@NonNull UUID key, @NonNull T value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };
    }

    public AsyncLoadingCache<UUID, T> getCache() {
        return cache;
    }
}
