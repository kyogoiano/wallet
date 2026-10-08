package br.com.wallet.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static br.com.wallet.support.IntegrationTestBase.NATS_CONTAINER;
import static br.com.wallet.support.IntegrationTestBase.REDIS;

public abstract class DockerProperties {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Forçamos a url para a chave correta que seu NatsConfig usa
        registry.add("nats.url", ()-> "nats://" + NATS_CONTAINER.getHost() + ":" + NATS_CONTAINER.getMappedPort(4222));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
