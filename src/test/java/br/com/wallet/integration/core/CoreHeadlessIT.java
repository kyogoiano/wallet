package br.com.wallet.integration.core;

import br.com.wallet.infrastructure.messaging.publisher.CoreStatusPublisher;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestBase.class)
@ActiveProfiles("test")
@DirtiesContext
@TestPropertySource(properties = {
        "wallet.runtime.mode=multi-process",
        "wallet.edge.enabled=false"
})
@DisplayName("CoreHeadlessIT: Headless Core Runtime Verification (REQ-PRC-002, I-PORT-001)")
class CoreHeadlessIT extends DockerProperties {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired(required = false)
    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        if (restTestClient == null) {
            restTestClient = RestTestClient.bindToApplicationContext(applicationContext).build();
        }
    }

    @Test
    @DisplayName("REQ-PRC-002 & I-PORT-001: Public Edge routes (/operations/*) must be 404 on headless Core")
    void shouldReturn404ForEdgeIngressRoutes() {
        restTestClient.post()
                .uri("/operations/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Core domain components and status publisher must be actively registered")
    void shouldRegisterCoreDomainBeans() {
        assertThat(applicationContext.containsBean("transferFundsUseCase") || applicationContext.containsBean("transferFundsService")).isTrue();
        assertThat(applicationContext.getBean(TransferFundsUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(CoreStatusPublisher.class)).isNotNull();
    }
}
