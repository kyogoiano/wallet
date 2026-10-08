package br.com.wallet.fraud.application;

import br.com.wallet.fraud.domain.FraudEngine;
import br.com.wallet.fraud.domain.FraudResponse;
import br.com.wallet.fraud.domain.context.FraudContext;
import org.springframework.stereotype.Service;

@Service
public class FraudService {

    private final FraudEngine engine;

    public FraudService(final FraudEngine engine) {
        this.engine = engine;
    }

    public FraudResponse check(final FraudContext context) {
        return engine.evaluate(context);
    }
}