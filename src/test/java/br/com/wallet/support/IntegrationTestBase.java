package br.com.wallet.support;

import br.com.wallet.integration.outbox.publisher.FailingEventPublisher;
import br.com.wallet.ledger.api.event.EventPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestBase {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private static final DockerImageName POSTGRES_FSYNC_OFF_IMAGE = DockerImageName.parse("postgres:18.3-alpine");

    /**
     * Optimized postgres image
     * fsync força o banco a esperar a gravação física no disco
     * synchronous_commit=off: O banco não espera o flush do log de transações para o disco.
     * full_page_writes=off: Desativa a proteção contra gravações parciais de página (comum após quedas de energia).
     * withTmpFs: Monta o diretório de dados do Postgres na memória RAM (tmpfs), o que é drasticamente mais rápido que o disco.
     * @return PostgreSQLContainer
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_FSYNC_OFF_IMAGE)
                .withReuse(true)
                .withDatabaseName("wallet")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("schema.sql");
    }

    private static final DockerImageName NATS_IMAGE_NAME = DockerImageName.parse("nats:2.14.5-alpine");

    public static final GenericContainer<?> NATS_CONTAINER = new GenericContainer<>(NATS_IMAGE_NAME)
            .withExposedPorts(4222, 8222)
            .withCommand("-js", "-sd", "/tmp", "--auth", "dfji348934jdd0i24uhjd29834ijrr0345jo0r3j034n", "-m", "8222")
            .waitingFor(
                    Wait.forHttp("/healthz")
                            .forPort(8222)
                            .forStatusCode(200)
            );

    private static final DockerImageName DRAGONFLY_IMAGE = DockerImageName.parse("docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1");
    public static final GenericContainer<?> REDIS = new GenericContainer<>(DRAGONFLY_IMAGE)
            .withExposedPorts(6379)
            .withCommand("--logtostderr", "--proactor_threads=2")
            .waitingFor(Wait.forListeningPort());
    public static final GenericContainer<?> DRAGONFLY = REDIS;

    static {
        NATS_CONTAINER.start();
        REDIS.start();
    }

    @Bean
    public GenericContainer<?> nats() {
        return NATS_CONTAINER;
    }

    @Bean
    @Primary
    public EventPublisher eventPublisher() {
        return new FailingEventPublisher();
    }
}
