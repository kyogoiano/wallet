package br.com.wallet.dlq.api.model;

/**
 * Architectural failure classification categories for DLQ operations (REQ-TDLQ-001).
 */
public enum DlqFailureType {
    TRANSIENT,
    PERMANENT,
    POISON,
    SECURITY;

    public static DlqFailureType from(final String value) {
        if (value == null) {
            return DlqFailureType.POISON;
        }
        try {
            return DlqFailureType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return DlqFailureType.POISON; // safe fallback
        }
    }
}
