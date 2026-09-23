package br.com.wallet.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static br.com.wallet.support.IntegrationTestBase.NATS_CONTAINER;
import static br.com.wallet.support.IntegrationTestBase.REDIS;

public abstract class DockerProperties {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (IntegrationTestBase.isDockerAvailable() && IntegrationTestBase.NATS_CONTAINER != null && IntegrationTestBase.REDIS != null) {
            registry.add("nats.url", () -> "nats://" + IntegrationTestBase.NATS_CONTAINER.getHost() + ":" + IntegrationTestBase.NATS_CONTAINER.getMappedPort(4222));
            registry.add("spring.data.redis.host", IntegrationTestBase.REDIS::getHost);
            registry.add("spring.data.redis.port", () -> IntegrationTestBase.REDIS.getMappedPort(6379));
        } else {
            registry.add("nats.url", () -> "nats://localhost:4222");
            registry.add("spring.data.redis.host", () -> "localhost");
            registry.add("spring.data.redis.port", () -> 6379);
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/wallet");
            registry.add("spring.datasource.username", () -> "wallet");
            registry.add("spring.datasource.password", () -> "wallet");
        }
    }
}
