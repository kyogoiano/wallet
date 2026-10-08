package br.com.wallet.fraud.domain;

public record RuleResult(
    RuleType ruleType,
    int scoreImpact,
    boolean triggered
) {}