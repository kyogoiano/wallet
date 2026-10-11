package br.com.wallet.integration.edge;

import br.com.wallet.core.security.SecurityHeaders;
import br.com.wallet.edge.api.CredentialMaterial;
import br.com.wallet.edge.internal.security.HmacCanonicalizer;
import br.com.wallet.edge.internal.security.HmacSignatureVerifier;
import br.com.wallet.edge.internal.security.InMemoryCredentialResolver;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.api.dto.OperationStatusResponse;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@TestPropertySource(properties = {
        "wallet.runtime.mode=monolith",
        "wallet.edge.enabled=true",
        "edge.spool.directory=${java.io.tmpdir}/edge-to-core-${random.uuid}"
})
@DisplayName("Edge-to-Core Full Pipeline End-to-End Integration Test (TASK-7.5)")
public class EdgeToCoreIntegrationTest extends DockerProperties {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

    @Autowired
    private BalanceUseCase balanceUseCase;

    @Autowired
    private OperationQueryUseCase operationQueryUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    private UUID walletA;
    private UUID walletB;

    @BeforeEach
    void setup() {
        cleaner.clean();
        walletA = UUID.randomUUID();
        walletB = UUID.randomUUID();

        createWalletUseCase.handle(new Wallet(walletA, BigDecimal.ONE, UUID.randomUUID(), UUID.randomUUID(), "tenant-alpha"));
        createWalletUseCase.handle(new Wallet(walletB, BigDecimal.ONE, UUID.randomUUID(), UUID.randomUUID(), "tenant-alpha"));

        // Seed initial balance in walletA: 200.00
        depositFundsUseCase.handle(new Deposit(walletA, null, new BigDecimal("200.00"), UUID.randomUUID(), "tenant-alpha"));
    }

    private final HmacSignatureVerifier signatureVerifier = new HmacSignatureVerifier();
    private final CredentialMaterial credentialMaterial =
            new CredentialMaterial(InMemoryCredentialResolver.DEFAULT_DEV_SECRET.getBytes(StandardCharsets.UTF_8));

    private String signRequest(String method, String path, String timestamp, @Nullable String opId, byte @Nullable [] body) {
        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                method,
                path,
                null,
                InMemoryCredentialResolver.DEFAULT_DEV_KEY_ID,
                timestamp,
                opId,
                body
        );
        return signatureVerifier.computeSignatureHex(canonical, credentialMaterial);
    }

    @Test
    @DisplayName("POST /operations/transfers accepts command with 202, NATS dispatches to Core, updates balances, and resolves terminal status")
    void shouldAcceptTransferAndExecuteEndToEnd() throws Exception {
        UUID opId = UUID.randomUUID();
        String requestJson = """
                {
                    "from": "%s",
                    "to": "%s",
                    "amount": 75.00
                }
                """.formatted(walletA, walletB);

        String timestamp = String.valueOf(System.currentTimeMillis());
        byte[] bodyBytes = requestJson.getBytes(StandardCharsets.UTF_8);
        String signature = signRequest("POST", "/operations/transfers", timestamp, opId.toString(), bodyBytes);

        // 1. Ingress accepts command and returns 202 ACCEPTED
        MvcResult mvcResult = mockMvc.perform(post("/operations/transfers")
                        .header(SecurityHeaders.IDEMPOTENCY_KEY, opId.toString())
                        .header(SecurityHeaders.X_KEY_ID, InMemoryCredentialResolver.DEFAULT_DEV_KEY_ID)
                        .header(SecurityHeaders.X_TIMESTAMP, timestamp)
                        .header(SecurityHeaders.X_SIGNATURE, signature)
                        .header(SecurityHeaders.X_NONCE, opId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andReturn();

        if (mvcResult.getRequest().isAsyncStarted()) {
            mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists("Location"));
        } else {
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(202);
            assertThat(mvcResult.getResponse().getHeader("Location")).isNotNull();
        }

        // 2. Poll/await until CoreCommandConsumer processes the message
        long start = System.currentTimeMillis();
        boolean completed = false;
        while (System.currentTimeMillis() - start < 10_000) {
            Optional<OperationStatusResponse> statusOpt = operationQueryUseCase.getOperationStatus(opId);
            if (statusOpt.isPresent() && statusOpt.get().status() == OperationStatus.COMPLETED) {
                completed = true;
                break;
            }
            Thread.sleep(100);
        }

        // 3. Verify ledger state consistency (I-BALANCE-001)
        assertThat(completed).isTrue();
        assertThat(balanceUseCase.getBalance(walletA)).isEqualByComparingTo(new BigDecimal("126.00"));
        assertThat(balanceUseCase.getBalance(walletB)).isEqualByComparingTo(new BigDecimal("76.00"));

        // 4. Verify SSE stream bootstrap receives COMPLETED immediately without hanging (I-EDGE-007)
        String streamTimestamp = String.valueOf(System.currentTimeMillis());
        String streamPath = "/operations/" + opId + "/stream";
        String streamSignature = signRequest("GET", streamPath, streamTimestamp, null, null);

        MvcResult sseResult = mockMvc.perform(get(streamPath)
                        .header(SecurityHeaders.X_KEY_ID, InMemoryCredentialResolver.DEFAULT_DEV_KEY_ID)
                        .header(SecurityHeaders.X_TIMESTAMP, streamTimestamp)
                        .header(SecurityHeaders.X_SIGNATURE, streamSignature)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andReturn();

        String sseBody = sseResult.getResponse().getContentAsString();
        assertThat(sseBody).contains("COMPLETED");
    }
}
