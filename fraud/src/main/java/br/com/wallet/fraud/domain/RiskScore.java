package br.com.wallet.fraud.domain;

public class RiskScore {

    private int score = 0;

    public void add(int value) {
        this.score += value;
    }

    public int value() {
        return score;
    }

    public FraudDecision decision() {
        if (score >= 12) return FraudDecision.BLOCK;
        if (score >= 7) return FraudDecision.REVIEW;
        return FraudDecision.ALLOW;
    }
}