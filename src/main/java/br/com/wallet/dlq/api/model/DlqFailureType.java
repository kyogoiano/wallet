package br.com.wallet.dlq.api.model;

public enum DlqFailureType {
    TRANSIENT,
    BUSINESS,
    POISON;

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
