package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.FraudRule;
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.domain.context.FraudContext;
import br.com.wallet.fraud.infrasctructure.RedisVelocityStore;
import org.jspecify.annotations.NonNull;

public class GlobalVelocityRule implements FraudRule {

    private final RedisVelocityStore redisVelocityStore;

    public GlobalVelocityRule(final RedisVelocityStore redisVelocityStore) {
        this.redisVelocityStore = redisVelocityStore;
    }

    @Override
    public RuleResult evaluate(@NonNull final FraudContext context) {

        var result = redisVelocityStore.checkVelocity(
                context.userId(),
                context.operationId(),
                context.timestamp()
        );

        return switch (result.status()) {

            case REPLAY -> // 🔥 critical: do NOT score again
                    new RuleResult(
                            RuleType.GLOBAL_VELOCITY,
                            0,
                            false
                    );

            case EXCEEDED -> new RuleResult(
                    RuleType.GLOBAL_VELOCITY,
                    30,
                    true
            );

            case OK -> new RuleResult(
                    RuleType.GLOBAL_VELOCITY,
                    0,
                    false
            );
        };
    }
}