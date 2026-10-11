package br.com.wallet.fraud.fusion.api.model;

/**
 * Entity subject types for scoped risk profiling (REQ-FUSION-006).
 */
public enum RiskSubjectType {
    USER,
    ACCOUNT,
    DEVICE,
    IP,
    PHONE,
    CARD,
    PIX_KEY
}
