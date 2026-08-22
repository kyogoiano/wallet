package br.com.wallet.wallet.internal.outbox;

public enum OutboxStatus {
    PENDING, FAILED, PROCESSING, PROCESSED, DEAD
}
