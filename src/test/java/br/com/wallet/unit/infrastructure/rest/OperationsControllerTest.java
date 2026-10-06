package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.infrastructure.rest.controller.OperationsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationsController.class)
class OperationsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OperationsController operationsController;

    @MockitoBean
    ApplicationEventPublisher eventPublisher;

    @MockitoBean
    FraudCheckHelper fraudCheckHelper;

    @MockitoBean
    OperationQueryUseCase operationQueryUseCase;

    @BeforeEach
    void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(operationsController, "eventPublisher", eventPublisher);
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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Transfer.class);
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

        verifyNoInteractions(eventPublisher);
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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Deposit.class);
        Deposit deposit = (Deposit) captor.getValue();
        assertThat(deposit.walletId()).isEqualTo(walletId);
        assertThat(deposit.userId()).isEqualTo(user);
        assertThat(deposit.amount()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(deposit.operationId()).isEqualTo(opId);
        assertThat(deposit.origin()).isEqualTo(OperationOrigin.USER);
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
    void shouldReturn500WhenPublishFails() throws Exception {

        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        doThrow(new RuntimeException("Publisher error"))
                .when(eventPublisher).publishEvent(any(Object.class));

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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Withdraw.class);
        Withdraw withdraw = (Withdraw) captor.getValue();
        assertThat(withdraw.walletId()).isEqualTo(walletId);
        assertThat(withdraw.amount()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(withdraw.operationId()).isEqualTo(opId);
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

    @Test
    void shouldGetOperationStatusSuccessfully() throws Exception {
        UUID opId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        var response = new br.com.wallet.ledger.api.dto.OperationStatusResponse(
                opId,
                br.com.wallet.ledger.api.domain.OperationStatus.FAILED,
                "Insufficient funds",
                "BUSINESS",
                now,
                now
        );
        when(operationQueryUseCase.getOperationStatus(opId)).thenReturn(java.util.Optional.of(response));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/operations/{operationId}", opId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(opId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Insufficient funds"))
                .andExpect(jsonPath("$.failureType").value("BUSINESS"));
    }

    @Test
    void shouldReturn404WhenOperationNotFound() throws Exception {
        UUID opId = UUID.randomUUID();
        when(operationQueryUseCase.getOperationStatus(opId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/operations/{operationId}", opId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
