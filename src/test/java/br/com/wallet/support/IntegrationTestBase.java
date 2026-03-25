package br.com.wallet.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;


@TestConfiguration(proxyBeanMethods = false)
@ActiveProfiles("test")
public class IntegrationTestBase {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18.3-alpine").withDatabaseName("wallet")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("schema.sql");
    }

}
