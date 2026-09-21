package br.com.wallet.fraud.infrastructure;

import io.lettuce.core.SetArgs;
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

    private String userKey(UUID userId, String suffix, String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        return "fraud:" + effectiveTenant + ":user:" + userId + ":" + suffix;
    }

    private String userKey(UUID userId, String suffix) {
        return userKey(userId, suffix, "default");
    }

    @Override
    CompletionStage<Boolean> isBlockedAsync(@NonNull final UUID userId) {
        return isBlockedAsync(userId, "default");
    }

    CompletionStage<Boolean> isBlockedAsync(@NonNull final UUID userId, final String tenantId) {
        log.debug("Checking if user {} is blocked in tenant {} (from Redis)", userId, tenantId);
        return commands.exists(userKey(userId, "blocked", tenantId)).thenApply(count -> count == 1);
    }

    /**
     * This method is cached and the cache expires using negative caching expiry rules at @expire method
     * @param userId user id is also the cache key
     * @return completion stage boolean, true if the user is blocked, false otherwise
     */
    public boolean isBlocked(@NonNull final UUID userId) {
        return isBlocked(userId, "default");
    }

    public boolean isBlocked(@NonNull final UUID userId, final String tenantId) {
        if (tenantId == null || "default".equals(tenantId)) {
            return this.getCache().get(userId).join();
        }
        return isBlockedAsync(userId, tenantId).toCompletableFuture().join();
    }

    public void setBlocked(@NonNull final UUID userId, final boolean blocked) {
        setBlocked(userId, blocked, "default");
    }

    public void setBlocked(@NonNull final UUID userId, final boolean blocked, final String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        log.info("Setting user blocked status in Redis: userId={}, tenantId={}, blocked={}", userId, effectiveTenant, blocked);
        String key = userKey(userId, "blocked", effectiveTenant);
        if (blocked) {
            commands.set(key, "1", SetArgs.Builder.ex(600));
        } else {
            commands.del(key);
        }
        if ("default".equals(effectiveTenant)) {
            getCache().put(userId, java.util.concurrent.CompletableFuture.completedFuture(blocked));
        }
    }
}
