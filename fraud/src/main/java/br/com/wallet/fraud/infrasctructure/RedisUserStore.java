package br.com.wallet.fraud.infrasctructure;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Component
public class RedisUserStore {

    private static final Logger log = LoggerFactory.getLogger(RedisUserStore.class);
    private final RedisAsyncCommands<String, String> commands;


    public RedisUserStore(final RedisAsyncCommands<String, String> commands) {
        this.commands = commands;
    }

    @Bean
    public AsyncLoadingCache<UUID, Boolean> fraudCache() {

        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .refreshAfterWrite(Duration.ofMinutes(2))
                .recordStats()
                .buildAsync((userId, executor) ->
                        this.isBlockedAsync(userId).exceptionally(ex -> {
                                    log.error("Redis failure", ex);
                                    return false; // fallback
                                }).toCompletableFuture()
                );
    }

    private String userKey(UUID userId, String suffix) {
        return "user:" + userId + ":" + suffix;
    }

    /**
     * This method is cached and the cache expires after 10 minutes
     * @param userId user id is also the cache key
     * @return completion stage boolean, true if the user is blocked, false otherwise
     */
    public CompletionStage<Boolean> isBlockedAsync(@NonNull final UUID userId) {
        log.debug("Checking if user {} is blocked (from Redis)", userId);
        return commands.exists(userKey(userId, "blocked")).thenApply(count -> count == 1);
    }

    public boolean isBlocked(UUID userId) {
        return fraudCache().get(userId).join();
    }
}
