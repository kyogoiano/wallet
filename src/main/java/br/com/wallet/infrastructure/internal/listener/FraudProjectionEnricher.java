package br.com.wallet.infrastructure.internal.listener;

import br.com.wallet.ledger.api.event.FraudEvent;
import br.com.wallet.fraud.domain.RuleType;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class FraudProjectionEnricher {
    private static final Logger log = LoggerFactory.getLogger(FraudProjectionEnricher.class);

    private static final String REVIEW_COUNT_PROTECTED_SCRIPT = """
            -- ============================================
            -- FRAUD PROCESSING SCRIPT (Atomic)
            -- ============================================

            -- KEYS:
            -- 1: replay key              -> fraud:op:{operationId} (global UUID/ULID, I-DF20-007)
            -- 2: review counter key      -> user:{tenantId}:{userId}:review_count
            -- 3: risk score key          -> user:{tenantId}:{userId}:risk_score
            -- 4: blocked flag key        -> user:{tenantId}:{userId}:blocked

            -- ARGV:
            -- 1: replay TTL (ms)         -> e.g. 30000
            -- 2: risk increment          -> e.g. riskScore from event
            -- 3: block threshold         -> e.g. 100 (risk score limit)
            -- 4: review threshold        -> e.g. 10 (max suspicious ops)

            -- ============================================
            -- STEP 1: Replay Protection
            -- ============================================

            -- If this operation was already processed, abort early
            if redis.call("exists", KEYS[1]) == 1 then
             return {0, "REPLAY_DETECTED"}
            end

            -- Mark operation as processed with TTL
            redis.call("psetex", KEYS[1], ARGV[1], "1")

            -- ============================================
            -- STEP 2: Increment Review Counter
            -- ============================================

            local reviewCount = redis.call("incr", KEYS[2])

            -- ============================================
            -- STEP 3: Accumulate Risk Score
            -- ============================================

            local riskScore = redis.call("incrby", KEYS[3], ARGV[2])

            -- ============================================
            -- STEP 4: Check Block Conditions
            -- ============================================

            local blocked = 0

            -- Condition 1: too many suspicious operations
            if tonumber(reviewCount) >= tonumber(ARGV[4]) then
             blocked = 1
            end

            -- Condition 2: accumulated risk score too high
            if tonumber(riskScore) >= tonumber(ARGV[3]) then
             blocked = 1
            end

            -- If any condition triggered → block user
            if blocked == 1 then
             redis.call("set", KEYS[4], "1")
            end

            -- ============================================
            -- STEP 5: Return structured result
            -- ============================================

            return {
             1,                  -- processed successfully
             reviewCount,        -- updated review count
             riskScore,          -- updated risk score
             blocked             -- 0 = not blocked, 1 = blocked
            }
       """;

    private static final String BLOCK_PROTECTED_SCRIPT = """
        -- ============================================
        -- BLOCK USER SCRIPT (Atomic + Idempotent)
        -- ============================================

        -- KEYS:
        -- 1: replay key         -> fraud:op:{operationId} (global UUID/ULID, I-DF20-007)
        -- 2: blocked key        -> user:{tenantId}:{userId}:blocked

        -- ARGV:
        -- 1: replay TTL (ms)    -> e.g. 30000
        -- 2: block TTL (sec)    -> e.g. 3600 (optional, 0 = permanent)

        -- ============================================
        -- STEP 1: Replay Protection
        -- ============================================

        if redis.call("exists", KEYS[1]) == 1 then
            return {0, "REPLAY_DETECTED"}
        end

        redis.call("psetex", KEYS[1], ARGV[1], "1")

        -- ============================================
        -- STEP 2: Block User (idempotent)
        -- ============================================

        -- only set if not already blocked
        if redis.call("exists", KEYS[2]) == 0 then
            if tonumber(ARGV[2]) > 0 then
                redis.call("set", KEYS[2], "1", "EX", ARGV[2])
            else
                redis.call("set", KEYS[2], "1")
            end
        end

        return {1, "BLOCKED"}
    """;

    private final RedisCommands<String, String> commands;

    public FraudProjectionEnricher(final RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    /**
     * This is a review count for operations protected against replays
     * @param fraudEvent fraud Event data
     */
    public void processReviewEvent(@NonNull final FraudEvent fraudEvent) {
        processReviewEvent(fraudEvent, fraudEvent.tenantId());
    }

    public void processReviewEvent(@NonNull final FraudEvent fraudEvent, String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "tenant-alpha";
        var result = commands.<List<Long>>eval(
                REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                new String[]{
                        "fraud:op:" + fraudEvent.operationId(),
                        userKey(effectiveTenant, fraudEvent.from(), "review_count"),
                        userKey(effectiveTenant, fraudEvent.from(), "risk_score"),
                        userKey(effectiveTenant, fraudEvent.from(), "blocked")
                },
                String.valueOf(30_000),              // replay TTL
                String.valueOf(fraudEvent.riskScore()),   // risk increment
                "100",                               // risk threshold
                "10"                                 // review threshold
        );

        // unpack
        Long processed = result.getFirst();
        Long reviewCount = result.get(1);
        Long riskScore = result.get(2);
        Long blocked = result.get(3);

        if (processed == 0) {
            log.debug(
                    "Fraud event already processed. operationId={}",
                    fraudEvent.operationId()
            );
            return;
        }

        if (blocked == 1) {
            log.warn("User {} blocked due to fraud risk. score={}, reviews={}",
                    fraudEvent.from(), riskScore, reviewCount);
        }

        if (fraudEvent.triggeredRules().contains(RuleType.GLOBAL_VELOCITY)) {
            commands.incr("user:" + effectiveTenant + ":" + fraudEvent.from() + ":velocity_hits");
        }
    }

    public void processBlockEvent(@NonNull final FraudEvent fraudEvent) {
        processBlockEvent(fraudEvent, fraudEvent.tenantId());
    }

    public void processBlockEvent(@NonNull final FraudEvent fraudEvent, String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "tenant-alpha";
        var result = commands.<List<Long>>eval(
                BLOCK_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                new String[]{
                        "fraud:op:" + fraudEvent.operationId(),
                        userKey(effectiveTenant, fraudEvent.from(), "blocked")
                },
                "30000",   // replay TTL
                "3600"     // block TTL (or "0" for permanent)
        );

        if (result.getFirst() == 0) {
            log.debug(
                    "Block event already processed. operationId={}",
                    fraudEvent.operationId()
            );
            return;
        }

        log.info(
                "User {} blocked.",
                fraudEvent.from()
        );
    }

    private String userKey(String tenantId, UUID userId, String suffix) {
        return "user:" + tenantId + ":" + userId + ":" + suffix;
    }
}
