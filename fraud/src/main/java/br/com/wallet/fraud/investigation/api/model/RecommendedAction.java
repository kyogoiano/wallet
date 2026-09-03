package br.com.wallet.fraud.investigation.api.model;

public enum RecommendedAction {
    MANUAL_REVIEW,
    REQUEST_ADDITIONAL_VERIFICATION,
    INCREASE_MONITORING,
    TEMPORARY_OUTGOING_RESTRICTION,
    ESCALATE_TO_COMPLIANCE
}
