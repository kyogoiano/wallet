package br.com.wallet.integration.ledger;

import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.api.event.FraudEvent;
import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.fraud.domain.FraudResponse;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.core.context.FraudContext;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
class FraudIT extends DockerProperties {

    private static final UUID from = UUID.randomUUID();
    private static final UUID fromUserId = UUID.randomUUID();
    private static final UUID to = UUID.randomUUID();
    private static final UUID toUserId = UUID.randomUUID();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @MockitoBean // Mock the actual FraudService to control its behavior
    private FraudService fraudService;

    @MockitoBean
    private OutboxDao<FraudEvent> outboxDao;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        // Default mock behavior: allow all fraud checks unless specified otherwise
        when(fraudService.check(any(FraudContext.class)))
                .thenReturn(new FraudResponse(FraudDecision.ALLOW, 0, List.of()));
    }

    @Test
    @DisplayName("Should block transfer operation if fraud service returns BLOCK decision")
    void shouldBlockTransferOperationOnFraudDecisionBlock() {
        // Given
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("200"), fromUserId, UUID.randomUUID()));
        when(fraudService.check(any(FraudContext.class))).thenReturn(new FraudResponse(FraudDecision.ALLOW, 0, List.of()));
        createWalletUseCase.handle(to,   toUserId);
        when(fraudService.check(any(FraudContext.class))).thenReturn(new FraudResponse(FraudDecision.ALLOW, 0, List.of()));
        UUID operationId = UUID.randomUUID();

        // Configure FraudService to return BLOCK
        when(fraudService.check(any(FraudContext.class)))
                .thenReturn(new FraudResponse(FraudDecision.BLOCK, 100, List.of(RuleType.SLIDING_WINDOW)));

        var body = """
            {
              "from": "%s",
              "to": "%s",
              "amount": 50
            }
        """.formatted(from, to);

        webTestClient.post()
                .uri("/operations/transfer")
                .header("Idempotency-Key", operationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange() // Automatically handles the async continuation
                .expectStatus().isForbidden() // Expect 403 Forbidden
                .expectBody()
                .jsonPath("$.code").isEqualTo("FRAUD_BLOCKED")
                .jsonPath("$.message").exists();

    }

    @Test
    @DisplayName("Should block deposit operation if fraud service returns BLOCK decision")
    void shouldBlockDepositOperationOnFraudDecisionBlock() {
        // Given
        var wallet = new Wallet(from, new BigDecimal("200"), fromUserId, UUID.randomUUID());
        createWalletUseCase.handle(wallet);
        UUID operationId = UUID.randomUUID();

        // Configure FraudService to return BLOCK
        when(fraudService.check(any(FraudContext.class)))
                .thenReturn(new FraudResponse(FraudDecision.BLOCK, 100, List.of(RuleType.GLOBAL_VELOCITY)));

        var body = """
            {
              "walletId": "%s",
              "userId": "%s",
              "amount": 100
            }
        """.formatted(from, fromUserId);

        webTestClient.post()
                .uri("/operations/deposit")
                .header("Idempotency-Key", operationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange() // Automatically handles the async continuation
                .expectStatus().isForbidden() // Expect 403 Forbidden
                .expectBody()
                .jsonPath("$.code").isEqualTo("FRAUD_BLOCKED")
                .jsonPath("$.message").exists();
    }

    @Test
    @DisplayName("Should block withdraw operation if fraud service returns BLOCK decision")
    void shouldBlockWithdrawOperationOnFraudDecisionBlock() {
        // Given
        var wallet = new Wallet(from, new BigDecimal("200"), fromUserId, UUID.randomUUID());
        createWalletUseCase.handle(wallet);
        UUID operationId = UUID.randomUUID();

        // Configure FraudService to return BLOCK
        when(fraudService.check(any(FraudContext.class)))
                .thenReturn(new FraudResponse(FraudDecision.BLOCK, 100, List.of(RuleType.NEW_RECIPIENT_MULE)));

        var body = """
            {
              "walletId": "%s",
              "userId": "%s",
              "amount": 50
            }
        """.formatted(from, fromUserId);

        webTestClient.post()
                .uri("/operations/withdraw")
                .header("Idempotency-Key", operationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange() // Automatically handles the async continuation
                .expectStatus().isForbidden() // Expect 403 Forbidden
                .expectBody()
                .jsonPath("$.code").isEqualTo("FRAUD_BLOCKED")
                .jsonPath("$.message").exists();
    }

    @Test
    @DisplayName("Should allow operation if fraud service returns ALLOW decision")
    void shouldAllowOperationOnFraudDecisionAllow() {
        // Given
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("200"), fromUserId, UUID.randomUUID()));
        createWalletUseCase.handle(to, toUserId);

        UUID operationId = UUID.randomUUID();

        // FraudService defaults to ALLOW, so no explicit mock needed here
        // when(fraudService.check(any(FraudContext.class))).thenReturn(new FraudResponse(FraudDecision.ALLOW, 0, List.of()));

        var body = """
            {
              "from": "%s",
              "to": "%s",
              "amount": 50
            }
        """.formatted(from, to);

        webTestClient.post()
                .uri("/operations/transfer")
                .header("Idempotency-Key", operationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange() // Automatically handles the async continuation
                .expectStatus().isAccepted();

        verify(fraudService, atLeast(1)).check(any(FraudContext.class));
        verify(outboxDao, atLeast(1)).save(any(FraudEvent.class));
    }
}
