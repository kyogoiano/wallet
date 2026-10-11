package br.com.wallet.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class DockerProperties {

    static {
        System.setProperty("otel.sdk.disabled", "true");
        System.setProperty("OTEL_SDK_DISABLED", "true");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("management.tracing.enabled", () -> "false");
        registry.add("management.opentelemetry.tracing.export.otlp.enabled", () -> "false");
        registry.add("management.opentelemetry.metrics.export.otlp.enabled", () -> "false");
        registry.add("management.opentelemetry.logging.export.otlp.enabled", () -> "false");
        registry.add("logging.opentelemetry.enabled", () -> "false");

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
