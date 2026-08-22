package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.wallet.api.context.Deposit;
import br.com.wallet.wallet.api.context.Withdraw;
import br.com.wallet.wallet.api.guard.FraudCheckHelper;
import br.com.wallet.infrastructure.messaging.publisher.NatsCommandPublisher;
import br.com.wallet.infrastructure.rest.controller.OperationsController;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationsController.class)
class OperationsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NatsCommandPublisher natsCommandPublisher;

    @MockitoBean
    FraudCheckHelper fraudCheckHelper;

    @BeforeEach
    void setup() {
        // By default, make the mock return a completed future to avoid NullPointerException in controller
        lenient().when(natsCommandPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(PublishAck.class)));
    }

    @Test
    void shouldTransferSuccessfully() throws Exception {

        var body = """
            {
              "from": "11111111-1111-1111-1111-111111111111",
              "to": "22222222-2222-2222-2222-222222222222",
              "amount": 50
            }
        """;

        mockMvc.perform(post("/operations/transfer")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(natsCommandPublisher).publishAsync(anyString(), any());
    }

    @Test
    void shouldReturn400WhenAmountIsInvalid() throws Exception {

        var body = """
        {
          "from": "11111111-1111-1111-1111-111111111111",
          "to": "22222222-2222-2222-2222-222222222222",
          "amount": -10
        }
    """;

        mockMvc.perform(post("/operations/transfer")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(natsCommandPublisher);
    }

    @Test
    void shouldFailWhenIdempotencyKeyMissing() throws Exception {

        var body = """
        {
          "from": "11111111-1111-1111-1111-111111111111",
          "to": "22222222-2222-2222-2222-222222222222",
          "amount": 50
        }
    """;

        mockMvc.perform(post("/operations/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.message")
                        .value("Required header 'Idempotency-Key' is missing"));
    }

    @Test
    void shouldDepositSuccessfully() throws Exception {

        UUID walletId = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        var body = """
                    {
                      "walletId": "%s",
                      "userId": "%s",
                      "amount": 100
                    }
                    """.formatted(walletId, user);

        mockMvc.perform(post("/operations/deposit")
                        .header("Idempotency-Key", opId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(natsCommandPublisher).publishAsync(eq("commands.deposit"), argThat(cmd ->
                cmd instanceof Deposit(
                        UUID id, UUID userId, BigDecimal amount, UUID operationId
                ) && id.equals(walletId) && Objects.requireNonNull(userId).equals(user) && amount.equals(new BigDecimal("100")) && operationId.equals(opId)
        ));
    }

    @Test
    void shouldFailDepositWhenIdempotencyKeyMissing() throws Exception {

        var body = """
                    {
                      "walletId": "%s",
                      "amount": 100
                    }
                    """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/operations/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailDepositWhenWalletIdMissing() throws Exception {

        UUID opId = UUID.randomUUID();

        var body = """
                    {
                      "amount": 100
                    }
                    """;

        mockMvc.perform(post("/operations/deposit")
                        .header("Idempotency-Key", opId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailDepositWhenAmountInvalid() throws Exception {

        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        var body = """
                    {
                      "walletId": "%s",
                      "amount": 0
                    }
                    """.formatted(walletId);

        mockMvc.perform(post("/operations/deposit")
                        .header("Idempotency-Key", opId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn500WhenNatsInitializationFails() throws Exception {

        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        // Simulate a failure before returning the future (e.g., connection issue)
        when(natsCommandPublisher.publishAsync(anyString(), any()))
                .thenThrow(new RuntimeException("NATS Down"));

        var body = """
                    {
                      "walletId": "%s",
                      "amount": 100
                    }
                    """.formatted(walletId);

        mockMvc.perform(post("/operations/deposit")
                        .header("Idempotency-Key", opId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldWithdrawSuccessfully() throws Exception {

        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        var body = """
                    {
                      "walletId": "%s",
                      "amount": 50
                    }
                    """.formatted(walletId);

        mockMvc.perform(post("/operations/withdraw")
                        .header("Idempotency-Key", opId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(natsCommandPublisher).publishAsync(eq("commands.withdraw"), argThat(cmd ->
                cmd instanceof Withdraw));
    }

    @Test
    void shouldFailWithdrawWhenWalletIdInvalid() throws Exception {

        UUID opId = UUID.randomUUID();

        var body = """
                    {
                      "walletId": "invalid",
                      "amount": 50
                    }
                    """;

        mockMvc.perform(post("/operations/withdraw")
                        .header("Idempotency-Key", opId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
