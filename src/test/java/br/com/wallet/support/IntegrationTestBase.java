package br.com.wallet.support;


import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.ObjectMapper;


@TestConfiguration(proxyBeanMethods = false)
@ActiveProfiles("test")
public class IntegrationTestBase {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18.3-alpine").withDatabaseName("wallet")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("schema.sql");
    }

    // Definimos como static para que ele inicie antes do Contexto do Spring
    public static final GenericContainer<?> NATS_CONTAINER = new GenericContainer<>("nats:2.12.6-alpine")
            .withExposedPorts(4222)
            .withCommand("-js")
            .waitingFor(Wait.forListeningPort());

    static {
        NATS_CONTAINER.start();
    }

    @Bean
    public GenericContainer<?> nats() {
        return NATS_CONTAINER;
    }




}
