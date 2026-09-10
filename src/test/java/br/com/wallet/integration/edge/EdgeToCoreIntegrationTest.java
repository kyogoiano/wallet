package br.com.wallet.integration.edge;

import br.com.wallet.edge.internal.ingress.OperationStatusAuthorizationFilter;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
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

        createWalletUseCase.handle(new Wallet(walletA, BigDecimal.ONE, UUID.randomUUID(), UUID.randomUUID()));
        createWalletUseCase.handle(new Wallet(walletB, BigDecimal.ONE, UUID.randomUUID(), UUID.randomUUID()));

        // Seed initial balance in walletA: 200.00
        depositFundsUseCase.handle(new Deposit(walletA, null, new BigDecimal("200.00"), UUID.randomUUID()));
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

        // 1. Ingress accepts command and returns 202 ACCEPTED
        MvcResult mvcResult = mockMvc.perform(post("/operations/transfers")
                        .header("Idempotency-Key", opId.toString())
                        .header(OperationStatusAuthorizationFilter.TENANT_HEADER, "tenant-authorized")
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
        MvcResult sseResult = mockMvc.perform(get("/operations/{operationId}/stream", opId)
                        .header(OperationStatusAuthorizationFilter.TENANT_HEADER, "tenant-authorized")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andReturn();

        String sseBody = sseResult.getResponse().getContentAsString();
        assertThat(sseBody).contains("COMPLETED");
    }
}
