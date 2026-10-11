package br.com.wallet.ledger.internal.outbox;

public enum OutboxStatus {
    PENDING, FAILED, PROCESSING, PROCESSED, DEAD
}
