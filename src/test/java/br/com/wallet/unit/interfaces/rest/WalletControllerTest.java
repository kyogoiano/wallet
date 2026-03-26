package br.com.wallet.unit.interfaces.rest;

import br.com.wallet.application.usecase.BalanceUseCase;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.LedgerUseCase;
import br.com.wallet.application.usecase.ReplayWalletUseCase;
import br.com.wallet.domain.LedgerEntry;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.interfaces.rest.controller.WalletController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BalanceUseCase balanceUseCase;

    @MockitoBean
    CreateWalletUseCase createWalletUseCase;

    @MockitoBean
    LedgerUseCase ledgerUseCase;

    @MockitoBean
    ReplayWalletUseCase replayWalletUseCase;

    @Test
    void shouldReturnBalance() throws Exception {

        UUID walletId = UUID.randomUUID();

        when(balanceUseCase.getBalance(walletId))
                .thenReturn(new BigDecimal("100"));

        mockMvc.perform(get("/wallets/{id}/balance", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void shouldReturn400WhenWalletDoesNotExist() throws Exception {
        UUID walletId = UUID.randomUUID();


        when(balanceUseCase.getBalance(walletId))
                .thenThrow(new IllegalArgumentException("Wallet not found"));

        mockMvc.perform(get("/wallets/{id}/balance", walletId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wallet not found"));

    }

    @Test
    void shouldReturn400WhenWalletIdIsInvalid() throws Exception {
        mockMvc.perform(get("/wallets/{id}/balance", "invalid-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnHistoricalBalance() throws Exception {
        UUID walletId = UUID.randomUUID();
        Instant instant = Instant.now();

        when(balanceUseCase.getHistoricalBalance(walletId, instant))
                .thenReturn(new BigDecimal("120.00"));

        mockMvc.perform(get("/wallets/{id}/balance/historical", walletId)
                        .param("at", instant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(120.00));
    }

    @Test
    void shouldReturn400WhenHistoricalParamMissing() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(get("/wallets/{id}/balance/historical", walletId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenHistoricalParamInvalid() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(get("/wallets/{id}/balance/historical", walletId)
                        .param("at", "invalid-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnLedgerEntries() throws Exception {
        UUID walletId = UUID.randomUUID();

        var entries = List.of(
                new LedgerEntry(UUID.randomUUID(), BigDecimal.TEN, LedgerType.CREDIT, UUID.randomUUID(), 1L, "hash", "previousHash", Instant.now())
        );

        when(ledgerUseCase.getLedger(walletId, 100))
                .thenReturn(entries);

        mockMvc.perform(get("/wallets/{id}/ledger", walletId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUseDefaultLimitWhenNotProvided() throws Exception {
        UUID walletId = UUID.randomUUID();

        when(ledgerUseCase.getLedger(walletId, 100))
                .thenReturn(List.of());

        mockMvc.perform(get("/wallets/{id}/ledger", walletId))
                .andExpect(status().isOk());

        verify(ledgerUseCase).getLedger(walletId, 100);
    }

    @Test
    void shouldReturn422WhenLimitTooLarge() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(get("/wallets/{id}/ledger", walletId)
                        .param("limit", "2000"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldReturn400WhenLimitInvalid() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(get("/wallets/{id}/ledger", walletId)
                        .param("limit", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenUseCaseThrowsIllegalArgument() throws Exception {
        UUID walletId = UUID.randomUUID();

        when(balanceUseCase.getBalance(walletId))
                .thenThrow(new IllegalArgumentException("Wallet not found"));

        mockMvc.perform(get("/wallets/{id}/balance", walletId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wallet not found"));
    }
}
