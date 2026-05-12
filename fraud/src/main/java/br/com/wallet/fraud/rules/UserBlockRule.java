package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.FraudRule;
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.domain.context.FraudContext;
import br.com.wallet.fraud.infrasctructure.RedisUserStore;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserBlockRule implements FraudRule {
    private final static Logger log = LoggerFactory.getLogger(UserBlockRule.class);
    private final RedisUserStore redisUserStore;

    public UserBlockRule(RedisUserStore redisUserStore) {
        this.redisUserStore = redisUserStore;
    }

    @Override
    public RuleResult evaluate(@NonNull final FraudContext context) {
        if (context.userId() == null) {
            return new RuleResult(RuleType.USER_BLOCK, 0, false);
        }
        final var triggered = redisUserStore.isBlocked(context.userId());
        log.debug("UserBlockRule.evaluate on operationId={}, userId={}: triggered={}", context.operationId(), context.userId(), triggered);
        return triggered ? new RuleResult(RuleType.USER_BLOCK, 30, true) :
                new RuleResult(RuleType.USER_BLOCK, 0, false);

    }
}
