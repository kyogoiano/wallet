package br.com.wallet.infrasctructure.messaging.consumer;


public interface NatsConsumerLifecycle {
    void start();
    void stop();
}
