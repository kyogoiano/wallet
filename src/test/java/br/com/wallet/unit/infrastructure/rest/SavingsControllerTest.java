package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.infrastructure.rest.controller.SavingsController;
import br.com.wallet.savings.api.SavingsPlanUseCase;
import br.com.wallet.savings.api.SavingsQueryUseCase;
import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleDto;
import br.com.wallet.savings.api.model.SavingsRuleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SavingsController.class)
@DisplayName("SavingsController Unit Tests")
class SavingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavingsPlanUseCase savingsPlanUseCase;

    @MockitoBean
    private SavingsQueryUseCase savingsQueryUseCase;

    private final UUID planId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final UUID sourceWallet = UUID.randomUUID();
    private final UUID targetWallet = UUID.randomUUID();

    @Test
    @DisplayName("Should create savings plan successfully (201 Created)")
    void shouldCreatePlanSuccessfully() throws Exception {
        SavingsPlanDto responseDto = new SavingsPlanDto(
                planId, sourceWallet, targetWallet, new BigDecimal("100.00"), "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanUseCase.createPlan(any(CreateSavingsPlanCommand.class))).thenReturn(responseDto);

        String requestBody = """
                {
                    "sourceWalletId": "%s",
                    "targetWalletId": "%s",
                    "minimumRetainedBalance": 100.00,
                    "rules": []
                }
                """.formatted(sourceWallet, targetWallet);

        mockMvc.perform(post("/savings/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.sourceWalletId").value(sourceWallet.toString()))
                .andExpect(jsonPath("$.targetWalletId").value(targetWallet.toString()));
    }

    @Test
    @DisplayName("Should get savings plan by ID (200 OK)")
    void shouldGetPlanById() throws Exception {
        SavingsPlanDto responseDto = new SavingsPlanDto(
                planId, sourceWallet, targetWallet, new BigDecimal("50.00"), "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanUseCase.getPlan(planId)).thenReturn(responseDto);

        mockMvc.perform(get("/savings/plans/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Should return 404 when savings plan not found")
    void shouldReturn404WhenPlanNotFound() throws Exception {
        when(savingsPlanUseCase.getPlan(planId)).thenThrow(new NoSuchElementException("Plan not found: " + planId));

        mockMvc.perform(get("/savings/plans/{planId}", planId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("wallet.not_found"));
    }

    @Test
    @DisplayName("Should get all savings plans for a wallet (200 OK)")
    void shouldGetPlansForWallet() throws Exception {
        SavingsPlanDto responseDto = new SavingsPlanDto(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanUseCase.getPlansForWallet(sourceWallet)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/savings/plans/wallet/{walletId}", sourceWallet))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planId.toString()));
    }

    @Test
    @DisplayName("Should pause and resume savings plan (204 No Content)")
    void shouldPauseAndResumePlan() throws Exception {
        mockMvc.perform(post("/savings/plans/{planId}/pause", planId))
                .andExpect(status().isNoContent());
        verify(savingsPlanUseCase).pausePlan(planId);

        mockMvc.perform(post("/savings/plans/{planId}/resume", planId))
                .andExpect(status().isNoContent());
        verify(savingsPlanUseCase).resumePlan(planId);
    }

    @Test
    @DisplayName("Should delete savings plan (204 No Content)")
    void shouldDeletePlan() throws Exception {
        mockMvc.perform(delete("/savings/plans/{planId}", planId))
                .andExpect(status().isNoContent());
        verify(savingsPlanUseCase).deletePlan(planId);
    }

    @Test
    @DisplayName("Should add rule to existing savings plan (201 Created)")
    void shouldAddRuleToPlan() throws Exception {
        SavingsRuleDto ruleDto = new SavingsRuleDto(
                ruleId, planId, SavingsRuleType.ROUND_UP, new BigDecimal("5.00"), null, null, true
        );
        when(savingsPlanUseCase.addRule(eq(planId), any(CreateSavingsRuleCommand.class))).thenReturn(ruleDto);

        String requestBody = """
                {
                    "ruleType": "ROUND_UP",
                    "stepAmount": 5.00
                }
                """;

        mockMvc.perform(post("/savings/plans/{planId}/rules", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ruleId.toString()))
                .andExpect(jsonPath("$.ruleType").value("ROUND_UP"))
                .andExpect(jsonPath("$.stepAmount").value(5.00));
    }

    @Test
    @DisplayName("Should get all rules for a plan (200 OK)")
    void shouldGetRulesForPlan() throws Exception {
        SavingsRuleDto ruleDto = new SavingsRuleDto(
                ruleId, planId, SavingsRuleType.PERCENTAGE, null, new BigDecimal("10.00"), null, true
        );
        when(savingsPlanUseCase.getRulesForPlan(planId)).thenReturn(List.of(ruleDto));

        mockMvc.perform(get("/savings/plans/{planId}/rules", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ruleId.toString()))
                .andExpect(jsonPath("$[0].ruleType").value("PERCENTAGE"));
    }

    @Test
    @DisplayName("Should toggle rule status (204 No Content)")
    void shouldToggleRuleStatus() throws Exception {
        mockMvc.perform(patch("/savings/rules/{ruleId}/status", ruleId)
                        .param("active", "false"))
                .andExpect(status().isNoContent());
        verify(savingsPlanUseCase).toggleRule(ruleId, false);
    }

    @Test
    @DisplayName("Should delete rule by ID (204 No Content)")
    void shouldDeleteRule() throws Exception {
        mockMvc.perform(delete("/savings/rules/{ruleId}", ruleId))
                .andExpect(status().isNoContent());
        verify(savingsPlanUseCase).removeRule(ruleId);
    }

    @Test
    @DisplayName("Should get savings metrics for wallet (200 OK)")
    void shouldGetMetrics() throws Exception {
        SavingsMetricsResponse metricsResponse = new SavingsMetricsResponse(
                sourceWallet, new BigDecimal("250.00"), 5L, Map.of("ROUND_UP", new BigDecimal("250.00"))
        );
        when(savingsQueryUseCase.getMetrics(sourceWallet)).thenReturn(metricsResponse);

        mockMvc.perform(get("/savings/metrics/{walletId}", sourceWallet))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(sourceWallet.toString()))
                .andExpect(jsonPath("$.totalSaved").value(250.00))
                .andExpect(jsonPath("$.executionCount").value(5));
    }
}
