package br.com.wallet.infrasctructure.messaging;

public interface EventPublisher {
    void publish(String eventType, String payload);
}
