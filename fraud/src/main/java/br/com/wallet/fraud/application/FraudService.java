package br.com.wallet.fraud.application;

import br.com.wallet.fraud.domain.FraudEngine;
import br.com.wallet.fraud.domain.FraudResponse;
import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.infrastructure.RedisUserStore;
import org.springframework.stereotype.Service;

@Service
public class FraudService {

    private final FraudEngine engine;
    private final RedisUserStore redisUserStore;

    public FraudService(final FraudEngine engine, final br.com.wallet.fraud.infrastructure.RedisUserStore redisUserStore) {
        this.engine = engine;
        this.redisUserStore = redisUserStore;
    }

    public FraudResponse check(final FraudContext context) {
        return engine.evaluate(context);
    }

    public void blockUser(final java.util.UUID userId) {
        redisUserStore.setBlocked(userId, true);
    }

    public void unblockUser(final java.util.UUID userId) {
        redisUserStore.setBlocked(userId, false);
    }
}