package br.com.wallet.infrastructure.messaging.dlq;

public enum DlqStatus {
    PENDING, PROCESSING, FAILED, COMPLETED
}
