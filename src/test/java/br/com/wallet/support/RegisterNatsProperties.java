package br.com.wallet.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static br.com.wallet.support.IntegrationTestBase.NATS_CONTAINER;

public abstract class RegisterNatsProperties {

    @DynamicPropertySource
    static void registerNatsProperties(DynamicPropertyRegistry registry) {
        // Forçamos a url para a chave correta que seu NatsConfig usa
        registry.add("nats.url", ()-> "nats://" + NATS_CONTAINER.getHost() + ":" + NATS_CONTAINER.getMappedPort(4222));
    }
}
