package br.com.wallet.fraud.infrasctructure;

import br.com.wallet.fraud.domain.VelocityResult;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class RedisVelocityStore implements VelocityStore {
    private static final Logger log = LoggerFactory.getLogger(RedisVelocityStore.class);

    private static final String VELOCITY_SCRIPT = """
                -- KEYS[1] = user:{userId}:tx_window
            
                -- ARGV:
                -- 1 = now (ms)
                -- 2 = window (ms)
                -- 3 = operationId
                -- 4 = threshold
            
                -- add event
                local added = redis.call("zadd", KEYS[1], "NX", ARGV[1], ARGV[3])
            
                if added == 0 then
                    -- already processed → replay
                    return {-1, redis.call("zcard", KEYS[1])}
                end
            
                -- remove old
                local min = 0
                local max = ARGV[1] - ARGV[2]
                redis.call("zremrangebyscore", KEYS[1], min, max)
            
                -- count
                local count = redis.call("zcard", KEYS[1])
            
                -- set ttl (avoid memory leak)
                if redis.call("ttl", KEYS[1]) == -1 then
                  redis.call("pexpire", KEYS[1], ARGV[2])
                end
            
                if count > tonumber(ARGV[4]) then
                    return {1, count} -- above threshold
                end
            
                return {0, count} -- below thresold
            """;
    public static final String THRESHOLD = "10";
    public static final String WINDOW = String.valueOf(30_000);


    private final RedisAsyncCommands<String, String> commands;

    public RedisVelocityStore(RedisAsyncCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    @Bulkhead(name ="redisVelocity", fallbackMethod = "fallbackVelocity")
    @CircuitBreaker(name = "redisVelocity", fallbackMethod = "fallbackVelocity")
    public VelocityResult checkVelocity(@NonNull final UUID userId, @NonNull final UUID operationId, @NonNull final Instant timestamp) {
        return checkVelocityAsync(userId, operationId, timestamp)
                .thenApply( velocityResult -> cacheSnapshot(userId, velocityResult))
                .toCompletableFuture()
                .join();
    }

    private VelocityResult cacheSnapshot(@NonNull final UUID userId, final @NonNull VelocityResult result) {
        if (!(result instanceof VelocityResult.Unknown)) {
            fallbackCache.put(userId, CompletableFuture.completedFuture(result));
        }
        return result;
    }

    private final AsyncLoadingCache<UUID, VelocityResult> fallbackCache =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(Duration.ofSeconds(60))
                    .buildAsync((userId, executor) ->
                            CompletableFuture.completedFuture(new VelocityResult.Unknown())
                    );

    private CompletionStage<VelocityResult> checkVelocityAsync(
            @NonNull final UUID userId,
            @NonNull final UUID operationId,
            @NonNull final Instant timestamp
    ) {

        return commands.<List<Long>>eval(
                VELOCITY_SCRIPT,
                ScriptOutputType.MULTI,
                new String[]{"user:" + userId + ":tx_window"},
                String.valueOf(timestamp.toEpochMilli()),
                WINDOW,
                operationId.toString(),
                THRESHOLD
        ).thenApply(result -> {
            long status = result.get(0);
            long count = result.get(1);

            return switch ((int) status) {
                case -1 -> new VelocityResult.Replay(count);
                case 1  -> new VelocityResult.Exceeded(count);
                default -> new VelocityResult.Ok(count);
            };
        });
    }

    public VelocityResult fallbackVelocity(UUID userId, UUID operationId, Instant timestamp, Throwable ex) {
        log.error("Redis unavailable for velocity check, userId={}, operationId={}, timestamp={}, now returning cached value", userId, operationId, timestamp, ex);
        return fallbackCache.get(userId)
                .toCompletableFuture()
                .join();
    }
}
