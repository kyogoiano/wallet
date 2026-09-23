package br.com.wallet.support;

import br.com.wallet.integration.outbox.publisher.FailingEventPublisher;
import br.com.wallet.ledger.api.event.EventPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestBase {


    private static final DockerImageName POSTGRES_FSYNC_OFF_IMAGE = DockerImageName.parse("pgvector/pgvector:pg18")
            .asCompatibleSubstituteFor("postgres");

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
    @Conditional(DockerAvailableCondition.class)
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_FSYNC_OFF_IMAGE)
                .withReuse(true)
                .withDatabaseName("wallet")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("schema.sql");
    }

    private static final DockerImageName NATS_IMAGE_NAME = DockerImageName.parse("nats:2.15.0-alpine");
    public static final DockerImageName DRAGONFLY_IMAGE_V2 = DockerImageName.parse("docker.dragonflydb.io/dragonflydb/dragonfly:v2.0.0");
    public static final DockerImageName DRAGONFLY_IMAGE_V1 = DockerImageName.parse("docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1");

    private static final boolean DOCKER_AVAILABLE;
    public static final GenericContainer<?> NATS_CONTAINER;
    public static final GenericContainer<?> REDIS;
    public static final GenericContainer<?> DRAGONFLY;

    static {
        boolean available = false;
        try {
            available = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
        }
        DOCKER_AVAILABLE = available;

        if (DOCKER_AVAILABLE) {
            NATS_CONTAINER = new GenericContainer<>(NATS_IMAGE_NAME)
                    .withExposedPorts(4222, 8222)
                    .withCommand("-js", "-sd", "/tmp", "--auth", "dfji348934jdd0i24uhjd29834ijrr0345jo0r3j034n", "-m", "8222")
                    .waitingFor(
                            Wait.forHttp("/healthz")
                                    .forPort(8222)
                                    .forStatusCode(200)
                    );
            REDIS = new GenericContainer<>(DRAGONFLY_IMAGE_V2)
                    .withExposedPorts(6379)
                    .withCommand("--logtostderr", "--proactor_threads=2")
                    .waitingFor(Wait.forListeningPort());
            DRAGONFLY = REDIS;

            NATS_CONTAINER.start();
            REDIS.start();
        } else {
            NATS_CONTAINER = null;
            REDIS = null;
            DRAGONFLY = null;
        }
    }

    public static boolean isDockerAvailable() {
        return DOCKER_AVAILABLE;
    }

    public static String getNatsUrl() {
        if (DOCKER_AVAILABLE && NATS_CONTAINER != null) {
            return "nats://" + NATS_CONTAINER.getHost() + ":" + NATS_CONTAINER.getMappedPort(4222);
        }
        return "nats://localhost:4222";
    }

    public static GenericContainer<?> createDragonflyV1Container() {
        return new GenericContainer<>(DRAGONFLY_IMAGE_V1)
                .withExposedPorts(6379)
                .withCommand("--logtostderr", "--proactor_threads=2")
                .waitingFor(Wait.forListeningPort());
    }

    public static GenericContainer<?> createDragonflyV2Container() {
        return new GenericContainer<>(DRAGONFLY_IMAGE_V2)
                .withExposedPorts(6379)
                .withCommand("--logtostderr", "--proactor_threads=2")
                .waitingFor(Wait.forListeningPort());
    }

    @Bean
    @Primary
    public EventPublisher eventPublisher() {
        return new FailingEventPublisher();
    }
}
