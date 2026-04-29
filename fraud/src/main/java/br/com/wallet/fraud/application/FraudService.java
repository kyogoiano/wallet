package br.com.wallet.fraud.application;

import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.fraud.domain.FraudEngine;
import br.com.wallet.fraud.domain.context.FraudContext;
import org.springframework.stereotype.Service;

@Service
public class FraudService {

    private final FraudEngine engine;

    public FraudService(FraudEngine engine) {
        this.engine = engine;
    }

    public FraudDecision check(final FraudContext context) {
        return engine.evaluate(context);
    }
}