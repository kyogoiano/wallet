package br.com.wallet.unit.interfaces.rest;

import br.com.wallet.application.usecase.DepositFundsUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.application.usecase.WithdrawFundsUseCase;
import br.com.wallet.domain.context.Deposit;
import br.com.wallet.domain.context.Withdraw;
import br.com.wallet.exceptions.InsufficientFundsException;
import br.com.wallet.interfaces.rest.controller.OperationsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

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
    TransferFundsUseCase transfer;

    @MockitoBean
    DepositFundsUseCase deposit;

    @MockitoBean
    WithdrawFundsUseCase withdraw;

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
                .andExpect(status().isNoContent());

        verify(transfer).handle(any());
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

        verifyNoInteractions(transfer);
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
        UUID opId = UUID.randomUUID();

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
                .andExpect(status().isNoContent());

        verify(deposit).handle(new Deposit(walletId, new BigDecimal("100"), opId));
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
    void shouldReturn500WhenDepositFails() throws Exception {

        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        doThrow(new IllegalStateException("Operation Failed!"))
                .when(deposit)
                .handle(argThat(cmd ->
                        walletId.equals(cmd.walletId()) &&
                                opId.equals(cmd.operationId())
                ));

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
                .andExpect(status().is5xxServerError());
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
                .andExpect(status().isNoContent());

        verify(withdraw).handle(new Withdraw(walletId, new BigDecimal("50"), opId));
    }

    @Test
    void shouldReturn422WhenInsufficientFunds() throws Exception {

        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        doThrow(new InsufficientFundsException())
                .when(withdraw)
                .handle(argThat(cmd ->
                        walletId.equals(cmd.walletId()) &&
                                opId.equals(cmd.operationId())
                ));

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
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Insufficient funds"));
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
