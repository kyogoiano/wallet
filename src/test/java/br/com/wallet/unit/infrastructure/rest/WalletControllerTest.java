package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.ledger.api.*;
import br.com.wallet.ledger.api.domain.LedgerEntry;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.infrastructure.messaging.publisher.NatsCommandPublisher;
import br.com.wallet.infrastructure.rest.controller.WalletController;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NatsCommandPublisher natsCommandPublisher;

    @MockitoBean
    BalanceUseCase balanceUseCase;

    @MockitoBean
    CreateWalletUseCase createWalletUseCase;

    @MockitoBean
    LedgerUseCase ledgerUseCase;

    @MockitoBean
    ReplayWalletUseCase replayWalletUseCase;

    @MockitoBean
    AccountUseCase accountUseCase;

    @MockitoBean
    FraudCheckHelper fraudCheckHelper;

    @BeforeEach
    void setup() {
        lenient().when(natsCommandPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(PublishAck.class)));
    }

    @Test
    void shouldReturnBalance() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(balanceUseCase.getBalance(walletId)).thenReturn(new BigDecimal("100"));

        mockMvc.perform(get("/wallets/{id}/balance", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void shouldCreateEmptyWalletSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/wallets/{userId}", userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletId").exists());

        verify(createWalletUseCase).handle(any(UUID.class), eq(userId));
        verifyNoInteractions(natsCommandPublisher);
    }

    @Test
    void shouldCreateWalletWithDepositSuccessfully() throws Exception {
        UUID opId = UUID.randomUUID();
        var body = """
                {
                  "initialBalance": 100,
                  "userId": "00000000-0000-0000-0000-000000000001"
                }
                """;

        // 1. Start async processing
        MvcResult mvcResult = mockMvc.perform(post("/wallets/deposit")
                        .header("Idempotency-Key", opId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 2. Wait for completion and verify results
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.walletId").exists());

        verify(natsCommandPublisher).publishAsync(eq("commands.wallet"), argThat(cmd ->
                cmd instanceof Wallet w && w.initialBalance().equals(new BigDecimal("100")) && w.operationId().equals(opId)
        ));
        verifyNoInteractions(createWalletUseCase);
    }

    @Test
    void shouldFailCreateWithDepositWhenIdempotencyKeyMissing() throws Exception {
        var body = """
                {
                  "initialBalance": 100
                }
                """;

        mockMvc.perform(post("/wallets/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnHistoricalBalance() throws Exception {
        UUID walletId = UUID.randomUUID();
        Instant instant = Instant.now();
        when(balanceUseCase.getHistoricalBalance(walletId, instant)).thenReturn(new BigDecimal("120.00"));

        mockMvc.perform(get("/wallets/{id}/balance/historical", walletId)
                        .param("at", instant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(120.00));
    }

    @Test
    void shouldReturnLedgerEntries() throws Exception {
        UUID walletId = UUID.randomUUID();
        var entries = List.of(
                new LedgerEntry(UUID.randomUUID(), BigDecimal.TEN, LedgerType.CREDIT, UUID.randomUUID(), UUID.randomUUID(),
                        1L, "hash", "previousHash", Instant.now())
        );
        when(ledgerUseCase.getLedger(walletId, 100)).thenReturn(entries);

        mockMvc.perform(get("/wallets/{id}/ledger", walletId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn422WhenLimitTooLarge() throws Exception {
        UUID walletId = UUID.randomUUID();
        mockMvc.perform(get("/wallets/{id}/ledger", walletId).param("limit", "2000"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void replayShouldReturnBalance() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(replayWalletUseCase.execute(walletId)).thenReturn(new BigDecimal("150.00"));

        mockMvc.perform(get("/wallets/{id}/replay", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.00));
    }
}
