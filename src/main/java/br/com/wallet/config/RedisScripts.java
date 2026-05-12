package br.com.wallet.config;

public class RedisScripts {
    // Lua script to optimize review operations and protect against replays
    public static final String REVIEW_COUNT_PROTECTED_SCRIPT = """
                -- ============================================
                -- FRAUD PROCESSING SCRIPT (Atomic)
                -- ============================================
           
                -- KEYS:
                -- 1: replay key              -> fraud:op:{operationId}
                -- 2: review counter key      -> user:{userId}:review_count
                -- 3: risk score key          -> user:{userId}:risk_score
                -- 4: blocked flag key        -> user:{userId}:blocked
           
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

    public static final String BLOCK_PROTECTED_SCRIPT = """
        -- ============================================
        -- BLOCK USER SCRIPT (Atomic + Idempotent)
        -- ============================================
    
        -- KEYS:
        -- 1: replay key         -> fraud:op:{operationId}
        -- 2: blocked key        -> user:{userId}:blocked
    
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
}
