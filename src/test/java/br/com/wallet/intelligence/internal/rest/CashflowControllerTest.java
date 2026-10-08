package br.com.wallet.intelligence.internal.rest;

import br.com.wallet.infrastructure.rest.exception.ApiExceptionHandler;
import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import br.com.wallet.intelligence.api.dto.CashflowSyncResponse;
import br.com.wallet.intelligence.api.model.CashflowStatus;
import br.com.wallet.intelligence.internal.service.CashflowForecastingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CashflowController Unit Tests (Phase 4, REQ-CASH-004, REQ-CASH-005, I-CASH-007)")
class CashflowControllerTest {

    @Mock
    private CashflowForecastingService forecastingService;

    private MockMvc mockMvc;

    private final String tenantId = "tenant-alpha";
    private final UUID walletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CashflowController controller = new CashflowController(forecastingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("REQ-CASH-004, I-CASH-007: GET projections resolves authenticated tenant and returns forecast")
    void getProjectionsSuccess() throws Exception {
        CashflowProjectionResponse response = new CashflowProjectionResponse(
                tenantId,
                walletId,
                new BigDecimal("150.00"),
                new BigDecimal("39.90"),
                new BigDecimal("120.00"),
                new BigDecimal("250.00"),
                new BigDecimal("0.00"),
                new BigDecimal("100.00"),
                CashflowStatus.DEFICIT_WARNING,
                new BigDecimal("200.00"),
                1
        );

        when(forecastingService.getProjections(eq(tenantId), eq(walletId)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/intelligence/cashflow/{walletId}/projections", walletId)
                        .requestAttr("wallet.tenant_id", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.currentBalance").value(150.00))
                .andExpect(jsonPath("$.liabilities7Days").value(39.90))
                .andExpect(jsonPath("$.liabilities14Days").value(120.00))
                .andExpect(jsonPath("$.liabilities30Days").value(250.00))
                .andExpect(jsonPath("$.shortfall14Days").value(0.00))
                .andExpect(jsonPath("$.shortfall30Days").value(100.00))
                .andExpect(jsonPath("$.status30Days").value("DEFICIT_WARNING"))
                .andExpect(jsonPath("$.normalizedMonthlyCommitted").value(200.00))
                .andExpect(jsonPath("$.activeInstallmentsCount").value(1));

        verify(forecastingService).getProjections(eq(tenantId), eq(walletId));
    }

    @Test
    @DisplayName("REQ-CASH-004, I-CASH-007: GET projections rejects request without authenticated tenant")
    void getProjectionsMissingTenantRejected() throws Exception {
        mockMvc.perform(get("/api/v1/intelligence/cashflow/{walletId}/projections", walletId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REQ-CASH-005, I-CASH-004: POST sync-goals triggers idempotent sync and returns profile values")
    void syncGoalsSuccess() throws Exception {
        CashflowSyncResponse response = new CashflowSyncResponse(
                walletId,
                userId,
                new BigDecimal("200.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("500.00"),
                true
        );

        when(forecastingService.syncGoals(eq(tenantId), eq(walletId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/intelligence/cashflow/{walletId}/sync-goals", walletId)
                        .requestAttr("wallet.tenant_id", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.monthlyCommittedExpenses").value(200.00))
                .andExpect(jsonPath("$.monthlyIncome").value(3000.00))
                .andExpect(jsonPath("$.minimumSafetyBuffer").value(500.00))
                .andExpect(jsonPath("$.synced").value(true));

        verify(forecastingService).syncGoals(eq(tenantId), eq(walletId));
    }

    @Test
    @DisplayName("REQ-CASH-005, I-CASH-007: POST sync-goals rejects request without authenticated tenant")
    void syncGoalsMissingTenantRejected() throws Exception {
        mockMvc.perform(post("/api/v1/intelligence/cashflow/{walletId}/sync-goals", walletId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
