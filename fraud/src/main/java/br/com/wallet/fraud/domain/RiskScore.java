package br.com.wallet.fraud.domain;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class RiskScore {

    private int score = 0;
    private final List<RuleType> triggeredRules = new ArrayList<>();

    public void add(@NonNull final RuleResult result) {
        if (result.triggered()) {
            score += result.scoreImpact();
            triggeredRules.add(result.ruleType());
        }
    }


    public int value() {
        return score;
    }

    public List<RuleType> triggeredRules() {
        return triggeredRules;
    }


    public FraudDecision decision() {
        if (score >= 80) return FraudDecision.BLOCK;
        if (score >= 40) return FraudDecision.REVIEW;
        return FraudDecision.ALLOW;
    }
}