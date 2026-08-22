package br.com.wallet.fraud.infrastructure;

import io.lettuce.core.api.async.RedisAsyncCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;


/**
 * Redis User Store, beyond async redis data, it uses a resilient async caching and a "negative controlled caching concept" to reduce TTL
 *
 */

@Component
public class RedisUserStore extends AsyncUserCache<Boolean> {

    private static final Logger log = LoggerFactory.getLogger(RedisUserStore.class);
    private final RedisAsyncCommands<String, String> commands;


    public RedisUserStore(final RedisAsyncCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    Boolean getFallbackValue(@NonNull final UUID userId) {
        return false; // fail-closed! TODO: store and get value on DB
    }

    /**
     * if user is blocked expires at 10 minutes, if not expires at 30 seconds
     * @param key user id
     * @param value boolean value
     * @param currentTime current time in nanoseconds
     * @return expiry time in nanoseconds
     */
    @Override
    long expire(@NonNull final UUID key, @NonNull final Boolean value, final long currentTime) {
        return value
                ? Duration.ofMinutes(10).toNanos()
                : Duration.ofSeconds(30).toNanos();
    }

    private String userKey(UUID userId, String suffix) {
        return "user:" + userId + ":" + suffix;
    }


    @Override
    CompletionStage<Boolean> isBlockedAsync(@NonNull final UUID userId) {
        log.debug("Checking if user {} is blocked (from Redis)", userId);
        return commands.exists(userKey(userId, "blocked")).thenApply(count -> count == 1);
    }

    /**
     * This method is cached and the cache expires using negative caching expiry rules at @expire method
     * @param userId user id is also the cache key
     * @return completion stage boolean, true if the user is blocked, false otherwise
     */
    public boolean isBlocked(@NonNull final UUID userId) {
        return this.getCache().get(userId).join();
    }
}
