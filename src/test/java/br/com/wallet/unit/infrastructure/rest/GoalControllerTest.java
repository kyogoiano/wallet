package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.goals.api.CashflowProfileUseCase;
import br.com.wallet.goals.api.FinancialGoalUseCase;
import br.com.wallet.goals.api.GoalQueryUseCase;
import br.com.wallet.goals.api.GoalStrategyUseCase;
import br.com.wallet.goals.api.dto.*;
import br.com.wallet.goals.api.model.*;
import br.com.wallet.infrastructure.rest.controller.GoalController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GoalController.class)
@DisplayName("GoalController Unit Tests")
class GoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialGoalUseCase goalUseCase;

    @MockitoBean
    private GoalQueryUseCase queryUseCase;

    @MockitoBean
    private CashflowProfileUseCase cashflowProfileUseCase;

    @MockitoBean
    private GoalStrategyUseCase strategyUseCase;

    private final UUID goalId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @Test
    @DisplayName("Should create goal successfully (201 Created)")
    void shouldCreateGoalSuccessfully() throws Exception {
        GoalResponse response = new GoalResponse(
                goalId, userId, walletId, null,
                "Emergency Fund", new BigDecimal("50000.00"),
                LocalDate.of(2028, 12, 31), GoalPriority.HIGH, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        when(goalUseCase.createGoal(any(CreateGoalCommand.class))).thenReturn(response);

        String requestBody = String.format("""
            {
                "userId": "%s",
                "walletId": "%s",
                "name": "Emergency Fund",
                "targetAmount": 50000.00,
                "targetDate": "2028-12-31",
                "priority": "HIGH"
            }
            """, userId, walletId);

        mockMvc.perform(post("/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(goalId.toString()))
                .andExpect(jsonPath("$.name").value("Emergency Fund"))
                .andExpect(jsonPath("$.targetAmount").value(50000.00))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("Should get goal by ID (200 OK)")
    void shouldGetGoalById() throws Exception {
        GoalResponse response = new GoalResponse(
                goalId, userId, walletId, null,
                "Emergency Fund", new BigDecimal("50000.00"),
                LocalDate.of(2028, 12, 31), GoalPriority.HIGH, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        when(queryUseCase.findGoalById(goalId)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/goals/{id}", goalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(goalId.toString()))
                .andExpect(jsonPath("$.name").value("Emergency Fund"));
    }

    @Test
    @DisplayName("Should return 404 when goal not found")
    void shouldReturn404WhenGoalNotFound() throws Exception {
        when(queryUseCase.findGoalById(goalId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/goals/{id}", goalId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should list goals for wallet (200 OK)")
    void shouldListGoalsForWallet() throws Exception {
        GoalResponse response = new GoalResponse(
                goalId, userId, walletId, null,
                "Emergency Fund", new BigDecimal("50000.00"),
                LocalDate.of(2028, 12, 31), GoalPriority.HIGH, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        when(queryUseCase.findGoalsByWalletId(walletId)).thenReturn(List.of(response));

        mockMvc.perform(get("/goals/wallet/{walletId}", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(goalId.toString()));
    }

    @Test
    @DisplayName("Should update goal successfully (200 OK)")
    void shouldUpdateGoalSuccessfully() throws Exception {
        GoalResponse response = new GoalResponse(
                goalId, userId, walletId, null,
                "Updated Name", new BigDecimal("60000.00"),
                LocalDate.of(2029, 1, 1), GoalPriority.CRITICAL, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        when(goalUseCase.updateGoal(eq(goalId), any(UpdateGoalCommand.class))).thenReturn(response);

        String requestBody = """
            {
                "name": "Updated Name",
                "targetAmount": 60000.00,
                "targetDate": "2029-01-01",
                "priority": "CRITICAL"
            }
            """;

        mockMvc.perform(put("/goals/{id}", goalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    @DisplayName("Should pause and resume goal (204 No Content)")
    void shouldPauseAndResumeGoal() throws Exception {
        mockMvc.perform(post("/goals/{id}/pause", goalId))
                .andExpect(status().isNoContent());
        verify(goalUseCase).pauseGoal(goalId);

        mockMvc.perform(post("/goals/{id}/resume", goalId))
                .andExpect(status().isNoContent());
        verify(goalUseCase).resumeGoal(goalId);
    }

    @Test
    @DisplayName("Should cancel goal (204 No Content)")
    void shouldCancelGoal() throws Exception {
        mockMvc.perform(delete("/goals/{id}", goalId))
                .andExpect(status().isNoContent());
        verify(goalUseCase).cancelGoal(goalId);
    }

    @Test
    @DisplayName("Should save cashflow profile successfully (200 OK)")
    void shouldSaveCashflowProfile() throws Exception {
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(), userId, walletId,
                new BigDecimal("12000.00"), new BigDecimal("6000.00"), new BigDecimal("2000.00"),
                Instant.now()
        );
        when(cashflowProfileUseCase.saveCashflowProfile(any(SaveCashflowProfileCommand.class))).thenReturn(profile);

        String requestBody = String.format("""
            {
                "userId": "%s",
                "walletId": "%s",
                "monthlyIncome": 12000.00,
                "monthlyCommittedExpenses": 6000.00,
                "minimumSafetyBuffer": 2000.00
            }
            """, userId, walletId);

        mockMvc.perform(put("/goals/cashflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(12000.00))
                .andExpect(jsonPath("$.monthlyCommittedExpenses").value(6000.00))
                .andExpect(jsonPath("$.minimumSafetyBuffer").value(2000.00));
    }

    @Test
    @DisplayName("Should calculate goal strategy (200 OK)")
    void shouldCalculateGoalStrategy() throws Exception {
        GoalStrategyResponse strategyResponse = new GoalStrategyResponse(
                goalId,
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("40000.00"),
                20,
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("2000.00"),
                GoalFeasibility.ON_TRACK,
                LocalDate.of(2028, 4, 30),
                LocalDate.of(2026, 8, 27)
        );
        when(strategyUseCase.calculateStrategy(eq(goalId), any(LocalDate.class))).thenReturn(strategyResponse);

        mockMvc.perform(get("/goals/{id}/strategy?evaluationDate=2026-08-27", goalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(goalId.toString()))
                .andExpect(jsonPath("$.feasibility").value("ON_TRACK"))
                .andExpect(jsonPath("$.requiredMonthlyContribution").value(2000.00))
                .andExpect(jsonPath("$.safeMonthlyContributionCapacity").value(3000.00));
    }

    @Test
    @DisplayName("Should simulate goal strategy statelessly (200 OK)")
    void shouldSimulateGoalStrategy() throws Exception {
        GoalStrategyResponse strategyResponse = new GoalStrategyResponse(
                null,
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("40000.00"),
                20,
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                GoalFeasibility.ON_TRACK,
                LocalDate.of(2028, 4, 30),
                LocalDate.of(2026, 8, 27)
        );
        when(strategyUseCase.simulate(any(SimulateGoalCommand.class), any(LocalDate.class))).thenReturn(strategyResponse);

        String requestBody = """
            {
                "targetAmount": 50000.00,
                "targetDate": "2028-12-31",
                "currentBalance": 10000.00,
                "monthlyIncome": 10000.00,
                "monthlyCommittedExpenses": 6000.00,
                "minimumSafetyBuffer": 2000.00
            }
            """;

        mockMvc.perform(post("/goals/simulate?evaluationDate=2026-08-27")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(50000.00))
                .andExpect(jsonPath("$.feasibility").value("ON_TRACK"));
    }

    @Test
    @DisplayName("Should get multi-goal waterfall strategy report (200 OK)")
    void shouldGetWalletStrategyReport() throws Exception {
        MultiGoalStrategyReport report = new MultiGoalStrategyReport(
                walletId,
                new BigDecimal("12000.00"),
                new BigDecimal("6000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                List.of(),
                LocalDate.of(2026, 8, 27)
        );
        when(strategyUseCase.evaluateWallet(eq(walletId), any(LocalDate.class))).thenReturn(report);

        mockMvc.perform(get("/goals/wallet/{walletId}/strategy-report?evaluationDate=2026-08-27", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.totalSafeCapacity").value(4000.00));
    }
}
