package br.com.wallet.fraud.domain;

public enum RuleType {
    SLIDING_WINDOW,
    NEW_RECIPIENT,
    NEW_RECIPIENT_RING,
    NEW_RECIPIENT_MULE,
    NEW_RECIPIENT_FAN_OUT,
    GLOBAL_VELOCITY,
    USER_BLOCK
}
