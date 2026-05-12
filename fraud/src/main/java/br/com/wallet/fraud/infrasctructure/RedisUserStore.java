package br.com.wallet.fraud.infrasctructure;

import io.lettuce.core.api.sync.RedisCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RedisUserStore {

    private static final Logger log = LoggerFactory.getLogger(RedisUserStore.class);
    private final RedisCommands<String, String> commands;

    public RedisUserStore(final RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    private String userKey(UUID userId, String suffix) {
        return "user:" + userId + ":" + suffix;
    }

    /**
     * This method is cached and the cache expires after 5 minutes
     * @param userId user id is also the cache key
     * @return  true if the user is blocked, false otherwise
     */
    @Cacheable(value = "fraudBlockedUsers", key = "#userId")
    public boolean isBlocked(@NonNull final UUID userId) {
        log.debug("Checking if user {} is blocked (from Redis)", userId);
        return commands.exists(userKey(userId, "blocked")) == 1;
    }
}
