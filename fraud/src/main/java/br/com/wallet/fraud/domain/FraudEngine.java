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

    public FraudDecision evaluate(final FraudContext context) {

        final var score = new RiskScore();

        rules.forEach(rule -> rule.evaluate(context, score));

        return score.decision();
    }
}