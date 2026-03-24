package br.com.wallet.infrasctructure.outbox;

public enum OutboxStatus {
    PENDING, FAILED, PROCESSED;
}
