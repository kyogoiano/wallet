package br.com.wallet.integration.savings;

import br.com.wallet.ledger.api.CreateWalletUseCase;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Savings REST End-to-End Integration Tests")
public class SavingsRestIT extends DockerProperties {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID walletA;
    private UUID walletB;
    private UUID walletC;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        walletA = UUID.randomUUID();
        walletB = UUID.randomUUID();
        walletC = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(walletA, userId);
        createWalletUseCase.handle(walletB, userId);
        createWalletUseCase.handle(walletC, userId);
    }

    @Test
    @DisplayName("Should execute end-to-end savings plan and rule lifecycle via REST API with source vs target isolation")
    void shouldExecuteSavingsPlanLifecycleViaRest() throws Exception {
        // 1. Create Savings Plan: walletA (source) -> walletB (target)
        String createPlanJson = """
                {
                    "sourceWalletId": "%s",
                    "targetWalletId": "%s",
                    "minimumRetainedBalance": 50.00,
                    "rules": []
                }
                """.formatted(walletA, walletB);

        MvcResult createResult = mockMvc.perform(post("/savings/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sourceWalletId").value(walletA.toString()))
                .andExpect(jsonPath("$.targetWalletId").value(walletB.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        JsonNode createdPlanNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID planId = UUID.fromString(createdPlanNode.get("id").asString());

        // 1.1 List all savings plans with pagination
        mockMvc.perform(get("/savings/plans")
                        .param("limit", "10")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(planId.toString()));

        // 2. Query by Source Wallet: walletA should have 1 plan, walletB should have 0
        mockMvc.perform(get("/savings/plans/source/{sourceWalletId}", walletA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(planId.toString()))
                .andExpect(jsonPath("$[0].sourceWalletId").value(walletA.toString()));

        mockMvc.perform(get("/savings/plans/source/{sourceWalletId}", walletB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // 3. Query by Target Wallet: walletB should have 1 plan, walletA should have 0
        mockMvc.perform(get("/savings/plans/target/{targetWalletId}", walletB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(planId.toString()))
                .andExpect(jsonPath("$[0].targetWalletId").value(walletB.toString()));

        mockMvc.perform(get("/savings/plans/target/{targetWalletId}", walletA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // 4. Query by General Wallet (source or target): walletA and walletB should both match
        mockMvc.perform(get("/savings/plans/wallet/{walletId}", walletA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/savings/plans/wallet/{walletId}", walletB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/savings/plans/wallet/{walletId}", walletC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 5. Add a Savings Rule to the plan
        String addRuleJson = """
                {
                    "ruleType": "PERCENTAGE",
                    "percentageRate": 15.00
                }
                """;

        MvcResult addRuleResult = mockMvc.perform(post("/savings/plans/{planId}/rules", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRuleJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.ruleType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.percentageRate").value(15.00))
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn();

        JsonNode ruleNode = objectMapper.readTree(addRuleResult.getResponse().getContentAsString());
        UUID ruleId = UUID.fromString(ruleNode.get("id").asString());

        // 6. Get Rules for Plan
        mockMvc.perform(get("/savings/plans/{planId}/rules", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ruleId.toString()));

        // 7. Toggle rule status
        mockMvc.perform(patch("/savings/rules/{ruleId}/status", ruleId)
                        .param("active", "false"))
                .andExpect(status().isNoContent());

        // 8. Pause Plan
        mockMvc.perform(post("/savings/plans/{planId}/pause", planId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/savings/plans/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // 9. Delete Plan
        mockMvc.perform(delete("/savings/plans/{planId}", planId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/savings/plans/{planId}", planId))
                .andExpect(status().isNotFound());
    }
}
