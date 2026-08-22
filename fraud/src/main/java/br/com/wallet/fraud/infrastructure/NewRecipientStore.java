package br.com.wallet.fraud.infrastructure;

import br.com.wallet.fraud.domain.RecipientRisk;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class NewRecipientStore {


    public static final String WINDOW = String.valueOf(30_000);
    public static final Integer FAN_OUT_THRESHOLD = 10;
    public static final Integer FAN_IN_THRESHOLD = 5;

    private static final String NEW_RECIPIENT_SCRIPT = """
                -- KEYS:
                -- 1 = user:{senderId}:recipients
                -- 2 = recipient:{recipientId}:senders
            
                -- ARGV:
                -- 1 = now
                -- 2 = window
                -- 3 = recipientId
                -- 4 = senderId
            
                local now = tonumber(ARGV[1])
                local window = tonumber(ARGV[2])
            
                -- =========================
                -- sender -> recipients
                -- =========================
            
                local recipientAdded = redis.call(
                 "zadd",
                 KEYS[1],
                 "NX",
                 now,
                 ARGV[3]
                )
            
                redis.call(
                 "zremrangebyscore",
                 KEYS[1],
                 0,
                 now - window
                )
            
                local recipientCount = redis.call(
                 "zcard",
                 KEYS[1]
                )
            
                -- =========================
                -- recipient -> senders
                -- =========================
            
                local senderAdded = redis.call(
                 "zadd",
                 KEYS[2],
                 "NX",
                 now,
                 ARGV[4]
                )
            
                redis.call(
                 "zremrangebyscore",
                 KEYS[2],
                 0,
                 now - window
                )
            
                local senderCount = redis.call(
                 "zcard",
                 KEYS[2]
                )
            
                -- ttl protection
            
                if redis.call("ttl", KEYS[1]) == -1 then
                 redis.call("pexpire", KEYS[1], window)
                end
            
                if redis.call("ttl", KEYS[2]) == -1 then
                 redis.call("pexpire", KEYS[2], window)
                end
            
                return {
                 recipientAdded,
                 recipientCount,
                 senderAdded,
                 senderCount
                }
            """;


    private final RedisAsyncCommands<String, String> commands;

    public NewRecipientStore(RedisAsyncCommands<String, String> commands) {
        this.commands = commands;
    }


    /**
     * Check new recipient heuristics
     * The order matters!
     * 
     * @param senderId sender id
     * @param recipientId recipient id
     * @param timestamp instant of execution
     * @return recipient risk future result
     */
    public CompletionStage<RecipientRisk> checkNewRecipient(@NonNull final UUID senderId,
                                                                 @Nullable final UUID recipientId,
                                                                 @NonNull Instant timestamp) {

        if (recipientId == null) {
            return CompletableFuture.completedFuture(new RecipientRisk.Normal());
        }

        String key1 = "user:" + senderId + ":recipients";
        String key2 = "recipient:" + recipientId + ":senders";

        return commands.<List<Long>>eval(
                NEW_RECIPIENT_SCRIPT,
                ScriptOutputType.MULTI,
                new String[]{key1, key2},
                String.valueOf(timestamp.toEpochMilli()),
                WINDOW,
                recipientId.toString(),
                senderId.toString()
        ).thenApply(result -> {
            int recipientAdded = Math.toIntExact(result.get(0));
            int recipientCount = Math.toIntExact(result.get(1));
            int senderAdded = Math.toIntExact(result.get(2));
            int senderCount = Math.toIntExact(result.get(3));

            if(recipientCount > FAN_OUT_THRESHOLD && senderCount > FAN_IN_THRESHOLD) {
                return new RecipientRisk.Ring(senderCount, recipientCount);
            }

            if(senderAdded == 1
                    && senderCount > FAN_OUT_THRESHOLD) {
                return new RecipientRisk.Mule(senderCount);
            }

            if (recipientAdded == 1
                    && recipientCount > FAN_OUT_THRESHOLD) {
                return new RecipientRisk.FanOut(recipientCount);
            }

            return new RecipientRisk.Normal();
        });
    }
}

