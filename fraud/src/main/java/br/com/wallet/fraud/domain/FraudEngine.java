package br.com.wallet.fraud.domain;

import br.com.wallet.fraud.domain.context.FraudContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FraudEngine {

    private final List<FraudRule> rules;

    public FraudEngine(final List<FraudRule> rules) {
        this.rules = rules;
    }

    public FraudResponse evaluate(final FraudContext context) {
        final var riskScore = new RiskScore();
        rules.forEach(rule -> riskScore.add(rule.evaluate(context)));
        return new FraudResponse(riskScore.decision(), riskScore.value(), riskScore.triggeredRules());
    }
}