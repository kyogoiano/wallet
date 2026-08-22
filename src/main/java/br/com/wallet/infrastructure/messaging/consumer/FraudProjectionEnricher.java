package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.infrastructure.config.RedisScripts;
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
    private final RedisCommands<String, String> commands;

    public FraudProjectionEnricher(final RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    /**
     * This is a review count for operations protected against replays
     * @param fraudEvent fraud Event data
     */
    public void processReviewEvent(@NonNull final FraudEvent fraudEvent) {
        var result = commands.<List<Long>>eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                new String[]{
                        "fraud:op:" + fraudEvent.operationId(),
                        userKey(fraudEvent.from(), "review_count"),
                        userKey(fraudEvent.from(), "risk_score"),
                        userKey(fraudEvent.from(), "blocked")
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
            commands.incr("user:" + fraudEvent.from() + ":velocity_hits");
        }

    }

    public void processBlockEvent(@NonNull final FraudEvent fraudEvent) {
        var result = commands.<List<Long>>eval(
                RedisScripts.BLOCK_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                new String[]{
                        "fraud:op:" + fraudEvent.operationId(),
                        userKey(fraudEvent.from(), "blocked")
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

    private String userKey(UUID userId, String suffix) {
        return "user:" + userId + ":" + suffix;
    }
}
