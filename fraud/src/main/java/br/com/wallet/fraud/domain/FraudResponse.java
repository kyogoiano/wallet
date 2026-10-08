package br.com.wallet.fraud.domain;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record FraudResponse(@NonNull FraudDecision fraudDecision, int riskScore, @NonNull List<RuleType> triggeredRules) {
}
