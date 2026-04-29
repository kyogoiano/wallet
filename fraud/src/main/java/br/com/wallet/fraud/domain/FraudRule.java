package br.com.wallet.fraud.domain;

import br.com.wallet.fraud.domain.context.FraudContext;

public interface FraudRule {
    void evaluate(FraudContext context, RiskScore score);
}
