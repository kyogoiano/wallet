package br.com.wallet.fraud.domain;

import br.com.wallet.core.context.FraudContext;

public interface FraudRule {
    RuleResult evaluate(FraudContext context);
}
