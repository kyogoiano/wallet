package br.com.wallet.infrasctructure.messaging.publisher;

public interface EventPublisher {
    void publish(String eventType, String payload);
}
