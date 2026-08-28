package br.com.wallet.integration.goals;

import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Goals & Strategy REST End-to-End Integration Tests")
public class GoalRestIT extends DockerProperties {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID walletA;
    private UUID walletB;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        walletA = UUID.randomUUID();
        walletB = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(walletA, userId);
        createWalletUseCase.handle(walletB, userId);
    }

    @Test
    @DisplayName("Complete E2E: Create goal, configure cashflow, calculate live strategy and simulate")
    void shouldExecuteFullGoalAndStrategyLifecycle() throws Exception {
        // 1. Create a goal
        String createGoalJson = String.format("""
            {
                "userId": "%s",
                "walletId": "%s",
                "targetWalletId": "%s",
                "name": "House Downpayment 2028",
                "targetAmount": 100000.00,
                "targetDate": "2028-12-31",
                "priority": "HIGH"
            }
            """, userId, walletA, walletB);

        MvcResult createResult = mockMvc.perform(post("/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGoalJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("House Downpayment 2028"))
                .andExpect(jsonPath("$.targetAmount").value(100000.00))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andReturn();

        JsonNode goalNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String goalId = goalNode.get("id").asString();

        // 2. Configure Cashflow Profile
        String cashflowJson = String.format("""
            {
                "userId": "%s",
                "walletId": "%s",
                "monthlyIncome": 12000.00,
                "monthlyCommittedExpenses": 6000.00,
                "minimumSafetyBuffer": 2000.00
            }
            """, userId, walletA);

        mockMvc.perform(put("/goals/cashflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashflowJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(12000.00))
                .andExpect(jsonPath("$.minimumSafetyBuffer").value(2000.00));

        // 3. Deposit 20,000 into target wallet
        depositFundsUseCase.handle(new Deposit(
                walletB,
                userId,
                new BigDecimal("20000.00"),
                UUID.randomUUID()
        ));

        // 4. Calculate live strategy (Deficit = 80,000; Months: 28; Required: 2857.14; Safe Capacity: 4000)
        mockMvc.perform(get("/goals/{id}/strategy?evaluationDate=2026-08-27", goalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.targetAmount").value(100000.00))
                .andExpect(jsonPath("$.currentAccumulatedAmount").value(20000.00))
                .andExpect(jsonPath("$.remainingDeficit").value(80000.00))
                .andExpect(jsonPath("$.safeMonthlyContributionCapacity").value(4000.00))
                .andExpect(jsonPath("$.feasibility").value("ON_TRACK"));

        // 5. Multi-goal strategy report for wallet
        mockMvc.perform(get("/goals/wallet/{walletId}/strategy-report?evaluationDate=2026-08-27", walletA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletA.toString()))
                .andExpect(jsonPath("$.totalSafeCapacity").value(4000.00))
                .andExpect(jsonPath("$.goalStrategies").isArray());

        // 6. Stateless simulation
        String simulateJson = """
            {
                "targetAmount": 50000.00,
                "targetDate": "2027-12-31",
                "currentBalance": 10000.00,
                "monthlyIncome": 10000.00,
                "monthlyCommittedExpenses": 5000.00,
                "minimumSafetyBuffer": 2000.00
            }
            """;

        mockMvc.perform(post("/goals/simulate?evaluationDate=2026-08-27")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(50000.00))
                .andExpect(jsonPath("$.remainingDeficit").value(40000.00))
                .andExpect(jsonPath("$.safeMonthlyContributionCapacity").value(3000.00));

        // 7. Pause and Resume Goal
        mockMvc.perform(post("/goals/{id}/pause", goalId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/goals/{id}", goalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(post("/goals/{id}/resume", goalId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/goals/{id}", goalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
