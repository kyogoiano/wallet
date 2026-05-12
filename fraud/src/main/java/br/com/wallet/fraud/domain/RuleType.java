package br.com.wallet.fraud.domain;

public enum RuleType {
    SLIDING_WINDOW,
    NEW_RECIPIENT,
    GLOBAL_VELOCITY,
    USER_BLOCK
}
