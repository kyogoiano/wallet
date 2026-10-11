package br.com.wallet.infrastructure.messaging.consumer;

public enum RetryDecision {
    RETRY, DLQ, ACK
}
