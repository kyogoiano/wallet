package br.com.wallet.integration.edge;

import br.com.wallet.edge.EdgeApplication;
import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EdgeApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "wallet.runtime.mode=multi-process",
        "wallet.edge.enabled=true",
        "edge.spool.directory=${java.io.tmpdir}/edge-standalone-it-${random.uuid}"
})
@DisplayName("EdgeStandaloneIT: Edge Independent Runtime & Zero-DB Verification (REQ-PRC-001, REQ-PRC-003, I-STATE-001)")
class EdgeStandaloneIT {

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private Connection connection;

    @Autowired(required = false)
    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() throws Exception {
        if (connection != null) {
            org.mockito.Mockito.lenient().when(connection.getStatus()).thenReturn(Connection.Status.CONNECTED);
            io.nats.client.JetStream js = org.mockito.Mockito.mock(io.nats.client.JetStream.class);
            io.nats.client.api.PublishAck ack = org.mockito.Mockito.mock(io.nats.client.api.PublishAck.class);
            org.mockito.Mockito.lenient().when(connection.jetStream()).thenReturn(js);
            org.mockito.Mockito.lenient().when(js.publishAsync(org.mockito.ArgumentMatchers.any(io.nats.client.Message.class), org.mockito.ArgumentMatchers.any(io.nats.client.PublishOptions.class)))
                    .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(ack));
        }
        if (restTestClient == null) {
            restTestClient = RestTestClient.bindToApplicationContext(applicationContext).build();
        }
    }

    @Test
    @DisplayName("REQ-PRC-003 & I-STATE-001: Edge runtime must contain ZERO relational database beans or Hikari pools")
    void assertNoDataSourceBeans() {
        // Assert absence of JDBC DataSource
        String[] dataSourceBeans = applicationContext.getBeanNamesForType(
                org.springframework.util.ClassUtils.resolveClassName("javax.sql.DataSource", getClass().getClassLoader())
        );
        assertThat(dataSourceBeans).isEmpty();

        // Assert absence of Hibernate / JPA EntityManagers
        boolean jpaPresent = org.springframework.util.ClassUtils.isPresent("jakarta.persistence.EntityManager", getClass().getClassLoader());
        if (jpaPresent) {
            Class<?> emClass = org.springframework.util.ClassUtils.resolveClassName("jakarta.persistence.EntityManager", getClass().getClassLoader());
            String[] emBeans = applicationContext.getBeanNamesForType(emClass);
            assertThat(emBeans).isEmpty();
        }
    }

    @Test
    @DisplayName("REQ-PRC-001 & I-EDGE-001: Standalone Edge must accept commands returning HTTP 202 ACCEPTED")
    void shouldAcceptTransferCommandStandalone() {
        UUID opId = UUID.randomUUID();
        String requestJson = """
                {
                    "sourceAccountId": "%s",
                    "targetAccountId": "%s",
                    "amount": 150.00
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        restTestClient.post()
                .uri("/operations/transfers")
                .header("Idempotency-Key", opId.toString())
                .header("X-Tenant-Id", "tenant-alpha")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().value("Location", loc -> assertThat(loc).contains(opId.toString()));
    }

    @Test
    @DisplayName("REQ-PRC-015: Actuator health probe must be accessible and report edge gateway state")
    void shouldExposeActuatorHealthProbe() {
        restTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");

        restTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
