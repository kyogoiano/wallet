package br.com.wallet.dlq.api.model;

public enum DlqStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    EXHAUSTED,
    DISCARDED
}
