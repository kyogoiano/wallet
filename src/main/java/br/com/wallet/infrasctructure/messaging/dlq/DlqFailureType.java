package br.com.wallet.infrasctructure.messaging.dlq;

public enum DlqFailureType {
    TRANSIENT, BUSINESS, POISON;

    public static DlqFailureType from(final String value) {
        try {
            return DlqFailureType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return DlqFailureType.POISON; // safe fallback
        }
    }
}
