package br.com.wallet.fraud.infrasctructure;

import br.com.wallet.fraud.domain.VelocityResult;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class RedisVelocityStore implements VelocityStore {

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


    private final RedisCommands<String, String> commands;

    public RedisVelocityStore(RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public VelocityResult checkVelocity(@NonNull UUID userId, @NonNull UUID operationId, @NonNull Instant timestamp) {
        var result = commands.<List<Long>>eval(VELOCITY_SCRIPT, ScriptOutputType.MULTI, new String[]{
                "user:" + userId + ":tx_window"
        }, String.valueOf(timestamp.toEpochMilli()), WINDOW, operationId.toString(), THRESHOLD);
        long status = result.get(0);
        long count = result.get(1);

        return switch ((int) status) {
            case -1 -> VelocityResult.replay(count);
            case 1  -> VelocityResult.exceeded(count);
            default -> VelocityResult.ok(count);
        };
    }
}
